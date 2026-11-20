package design.inherent.skyweavecompat.probe;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import design.inherent.skyweavecompat.Feature;
import design.inherent.skyweavecompat.OptionalIntegrations;
import design.inherent.skyweavecompat.configuration.ConfigLocations;
import design.inherent.skyweavecompat.configuration.SettingsPolicy;
import design.inherent.skyweavecompat.terrain.structure.SearchSettings;
import design.inherent.skyweavecompat.terrain.structure.StructureLocator;
import design.inherent.skyweavecompat.wayfinder.Wayfinder;
import design.inherent.skyweavecompat.wayfinder.WayfinderSettings;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** The real legacy command delegates to the current shared server policy, through a logged-in op. */
final class SettingsPolicyProbe {
    private static final String PREFIX = "skyweave_compat.settings.policy_";
    private final JsonObject report = new JsonObject();
    private final ArrayList<Component> messages = new ArrayList<>();
    private boolean ready, started, finished;
    private ServerPlayer player;
    private CompletableFuture<Void> awaiting;
    private String expected;
    private int began, ticks, stableChunks, lastChunks = -1;

    SettingsPolicyProbe() {
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> ready = true);
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> tick(event.getServer()));
    }

    private void tick(MinecraftServer server) {
        if (!ready || finished) return;
        ticks++;
        if (ticks < 40) return;
        try {
            if (!started) {
                if (player == null) {
                    var root = FMLPaths.GAMEDIR.get().toRealPath();
                    var marker = root.resolve("settings-policy-fixture.json");
                    check(Files.isRegularFile(marker) && Files.readString(marker).contains("mc-modpacks/config-system"),
                            "Missing owned policy fixture marker");
                    player = ProbePlayers.stationary(server, "SettingsPolicy", server.overworld().getSharedSpawnPos().above(2));
                    server.getPlayerList().op(player.getGameProfile());
                }
                int loaded = server.overworld().getChunkSource().getLoadedChunksCount();
                stableChunks = loaded == lastChunks ? stableChunks + 1 : 0;
                lastChunks = loaded;
                check(ticks < 300, "Native spawn and actor chunk tracking did not settle");
                if (stableChunks < 24) return;
                started = true;
                report.addProperty("checkedLoadedChunks", loaded);
                run(server, ConfigLocations.ownedDirectory(FMLPaths.CONFIGDIR.get())).whenComplete((ignored, failure) -> finish(server, failure));
            }
            if (awaiting != null) {
                ProbePlayers.drainChat(player, messages);
                var reply = messages.stream().flatMap(message -> keys(message))
                        .filter(key -> key.startsWith(PREFIX)).findFirst().orElse(null);
                if (reply != null) {
                    var completion = awaiting;
                    awaiting = null;
                    messages.clear();
                    check(reply.equals(PREFIX + expected), "Unexpected policy completion " + reply);
                    completion.complete(null);
                } else check(ticks - began < 200, "Timed out waiting for a native policy completion");
            }
        } catch (Throwable failure) { finish(server, failure); }
    }

    private CompletableFuture<Void> run(MinecraftServer server, Path root) throws Exception {
        var source = player.createCommandSourceStack();
        var denied = source.withPermission(0);
        boolean terrain = OptionalIntegrations.enabled(Feature.TERRAIN);
        check(terrain == Boolean.getBoolean("neighbors.probe.settingsTerrain"), "Unexpected terrain admission");
        int chunks = server.overworld().getChunkSource().getLoadedChunksCount();
        var initial = Wayfinder.readPolicy(source);
        var before = initial.value();
        var candidate = new WayfinderSettings(true, 12, 8, 128, 2, 80, before.blockedBlocks());
        try {
            execute(denied, "wayfinder");
            throw new AssertionError("Non-operator command executed");
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException refused) {
            report.addProperty("permissionDenied", true);
        }
        refused(() -> Wayfinder.readPolicy(denied), SecurityException.class);
        var threadFailure = new java.util.concurrent.atomic.AtomicBoolean();
        var thread = new Thread(() -> {
            try { Wayfinder.readPolicy(source); } catch (IllegalStateException expected) { threadFailure.set(true); }
        });
        thread.start();
        thread.join();
        check(threadFailure.get(), "Foreign-thread policy read accepted");
        report.addProperty("actual_server_thread_guard", true);

        return policy(source, "wayfinder", root.resolve(WayfinderSettings.FILE), Wayfinder::readPolicy, candidate,
                "{\"operatorsOnly\":false}").thenCompose(ignored -> {
            if (!terrain) return CompletableFuture.completedFuture(null);
            return policy(source, "search", root.resolve(SearchSettings.FILE), StructureLocator::readPolicy,
                    new SearchSettings(128, 5, 1, 1), "{}");
        }).thenRun(() -> {
            int after = server.overworld().getChunkSource().getLoadedChunksCount();
            check(chunks == after, "Policy operations changed the loaded-chunk count from " + chunks + " to " + after);
            report.addProperty("loaded_chunk_count_unchanged", true);
            try { SettingsContextProbe.run(server, report); }
            catch (Exception failure) { throw new CompletionException(failure); }
            report.addProperty("limits", "Native command with embedded operator; no TCP or integrated GUI assertion");
        });
    }

    private <T> CompletableFuture<Void> policy(CommandSourceStack source, String destination, Path file,
            Function<CommandSourceStack, SettingsPolicy.Snapshot<T>> read, T candidate, String invalid) {
        final String original, authored = new GsonBuilder().setPrettyPrinting().create().toJson(candidate) + "\n";
        try { original = Files.readString(file); }
        catch (java.io.IOException failure) { return CompletableFuture.failedFuture(failure); }
        var before = read.apply(source);
        write(file, authored);
        return request(source, destination, "reloaded").thenCompose(ignored -> {
            var after = read.apply(source);
            check(after.value().equals(candidate) && after.revision() != before.revision(), "Actual policy was not applied");
            check(bytes(file).equals(authored), "Apply-only rewrote the authored file");
            return request(source, destination, "unchanged").thenCompose(nothing -> {
                check(read.apply(source).revision() == after.revision(), "No-op changed the live revision");
                write(file, invalid);
                return request(source, destination, "refused").thenCompose(refused -> {
                    check(read.apply(source).revision() == after.revision() && bytes(file).equals(invalid),
                            "Invalid reload rewrote saved bytes or active policy");
                    write(file, original);
                    return request(source, destination, "reloaded").thenRun(() -> {
                        check(read.apply(source).value().equals(before.value()) && bytes(file).equals(original),
                                "Original policy did not restore without writing");
                        report.addProperty(destination + "_reload_noop_and_invalid", true);
                    });
                });
            });
        });
    }

    private CompletableFuture<Void> request(CommandSourceStack source, String destination, String reply) {
        try {
            check(awaiting == null, "Previous native command still awaits its result");
            check(execute(source, destination) == 1, "Native reload was not admitted");
            expected = reply;
            began = ticks;
            awaiting = new CompletableFuture<>();
            return awaiting;
        } catch (Exception failure) { return CompletableFuture.failedFuture(failure); }
    }

    private static int execute(CommandSourceStack source, String destination) throws Exception {
        return source.getServer().getCommands().getDispatcher().execute("skyweavecompat settings reload " + destination, source);
    }

    private static java.util.stream.Stream<String> keys(Component value) {
        var own = value.getContents() instanceof TranslatableContents translated
                ? java.util.stream.Stream.of(translated.getKey()) : java.util.stream.Stream.<String>empty();
        return java.util.stream.Stream.concat(own, value.getSiblings().stream().flatMap(SettingsPolicyProbe::keys));
    }

    private void finish(MinecraftServer server, Throwable failure) {
        if (finished) return;
        finished = true;
        report.addProperty("complete", failure == null);
        if (failure != null) {
            report.addProperty("failure", failure.toString());
            failure.printStackTrace();
        }
        try { Files.writeString(Path.of(System.getProperty("neighbors.probe.output")), new GsonBuilder().setPrettyPrinting().create().toJson(report)); }
        catch (Exception writeFailed) { throw new IllegalStateException(writeFailed); }
        server.halt(false);
    }

    private static String bytes(Path file) {
        try { return Files.readString(file); }
        catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }

    private static void write(Path file, String text) {
        try { Files.writeString(file, text); }
        catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }

    private static void refused(Runnable run, Class<? extends RuntimeException> type) {
        try { run.run(); }
        catch (RuntimeException failure) { if (type.isInstance(failure)) return; throw failure; }
        throw new AssertionError("Expected " + type.getSimpleName());
    }

    private static void check(boolean value, String reason) {
        if (!value) throw new AssertionError(reason);
    }
}
