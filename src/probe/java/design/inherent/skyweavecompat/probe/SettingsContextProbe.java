package design.inherent.skyweavecompat.probe;

import com.google.gson.JsonObject;
import design.inherent.reform.neoforge.configuration.ServerSettings;
import design.inherent.reform.operation.DocumentActions;
import design.inherent.reform.operation.OperationDescriptor;
import design.inherent.reform.operation.OperationHandle;
import design.inherent.reform.owner.CommitReceipt;
import design.inherent.reform.owner.PolicyRefusal;
import design.inherent.skyweavecompat.configuration.ConfigLocations;
import design.inherent.skyweavecompat.configuration.PolicyDefinitions;
import design.inherent.skyweavecompat.wayfinder.Wayfinder;
import design.inherent.skyweavecompat.wayfinder.WayfinderSettings;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.neoforged.fml.loading.FMLPaths;

/** Actual shared production owners with native player permissions and connection replacement. */
final class SettingsContextProbe {
    private static final OperationDescriptor.EntryPoint ENTRY = OperationDescriptor.EntryPoint.COMMAND;
    private final MinecraftServer server;
    private final JsonObject report;
    private final ServerSettings.Document<WayfinderSettings> document;
    private final ArrayList<ServerSettings.Editor<?>> editors = new ArrayList<>();
    private final CommonListenerCookie cookie;
    private final Path path;
    private final String original;
    private ServerPlayer player;
    private DocumentActions<WayfinderSettings> actions;

    private SettingsContextProbe(MinecraftServer server, JsonObject report) throws Exception {
        this.server = server; this.report = report;
        check(Files.isRegularFile(FMLPaths.GAMEDIR.get().resolve("settings-policy-fixture.json")), "Missing policy fixture marker");
        document = ServerSettings.admittedDocument(server, PolicyDefinitions.WAYFINDER.id(), PolicyDefinitions.WAYFINDER).orElseThrow();
        path = ConfigLocations.file(FMLPaths.CONFIGDIR.get(), WayfinderSettings.FILE);
        original = Files.readString(path);
        cookie = new CommonListenerCookie(new com.mojang.authlib.GameProfile(UUID.randomUUID(), "ConfigGuest"), 0,
                net.minecraft.server.level.ClientInformation.createDefault(), false,
                net.neoforged.neoforge.network.connection.ConnectionType.NEOFORGE);
        player = ProbePlayers.login(server, cookie);
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
    }

    static CompletableFuture<Void> run(MinecraftServer server, JsonObject report) throws Exception {
        var fixture = new SettingsContextProbe(server, report);
        return fixture.run().whenCompleteAsync((ignored, failure) -> fixture.close(), server);
    }

    private DocumentActions<WayfinderSettings> open() {
        var editor = document.editor(player.createCommandSourceStack());
        editors.add(editor);
        return editor.actions();
    }

    private CompletableFuture<Void> run() {
        return refused(() -> open().load(ENTRY)).thenComposeAsync(ignored -> {
            server.getPlayerList().op(player.getGameProfile());
            actions = open();
            return result(actions.load(ENTRY));
        }, server).thenComposeAsync(ignored -> result(actions.edit(ENTRY, "stepsPerTick", "144")), server)
                .thenComposeAsync(ignored -> {
                    server.getPlayerList().deop(player.getGameProfile());
                    return refused(() -> actions.save(ENTRY));
                }, server).thenComposeAsync(ignored -> {
                    check(read().equals(original), "Revoked operator changed the source");
                    server.getPlayerList().op(player.getGameProfile());
                    return result(actions.save(ENTRY));
                }, server).thenComposeAsync(saved -> {
                    var receipt = saved.receipt().orElseThrow();
                    check(receipt.persistence() == CommitReceipt.Persistence.SAVED
                            && receipt.application() == CommitReceipt.Application.APPLIED, "Real owner did not save and publish");
                    check(Wayfinder.readPolicy(player.createCommandSourceStack()).value().stepsPerTick() == 144,
                            "Actual policy did not observe the shared save");
                    report.addProperty("context_shared_save_and_native_publication", true);
                    return result(actions.edit(ENTRY, "stepsPerTick", "160"));
                }, server).thenComposeAsync(ignored -> {
                    String competing = read() + " \n";
                    write(competing);
                    return result(actions.save(ENTRY)).thenAcceptAsync(saved -> {
                        check(saved.receipt().isEmpty() || !saved.receipt().orElseThrow().acknowledgesBaseline(),
                                "Stale source acknowledged a save");
                        check(read().equals(competing), "Stale save replaced the competing bytes");
                        check(Wayfinder.readPolicy(player.createCommandSourceStack()).value().stepsPerTick() == 144,
                                "Stale save changed runtime policy");
                        report.addProperty("context_stale_source_retains_file_and_policy", true);
                    }, server);
                }, server).thenComposeAsync(ignored -> {
                    var closed = open(); editors.getLast().close();
                    return refused(() -> closed.load(ENTRY));
                }, server).thenComposeAsync(ignored -> {
                    actions = open();
                    return result(actions.load(ENTRY));
                }, server).thenComposeAsync(ignored -> {
                    server.getPlayerList().remove(player);
                    player = ProbePlayers.login(server, cookie);
                    server.getPlayerList().op(player.getGameProfile());
                    return refused(() -> actions.inspect(ENTRY));
                }, server).thenRunAsync(() -> {
                    report.addProperty("context_current_permission_and_revocation", true);
                    report.addProperty("context_closed_and_same_uuid_replacement_refused", true);
                    report.addProperty("context_scope", "Actual shared production owner and embedded native connections; no TCP or two-server-lifetime assertion");
                }, server);
    }

    private CompletableFuture<Void> refused(Supplier<? extends OperationHandle<?>> operation) {
        try {
            return result(operation.get()).handleAsync((ignored, failure) -> {
                check(failure != null, "Unauthorized or closed operation succeeded");
                Throwable cause = failure;
                while (cause instanceof java.util.concurrent.CompletionException && cause.getCause() != null) cause = cause.getCause();
                check(cause instanceof PolicyRefusal || cause instanceof IllegalStateException,
                        "Unexpected refusal: " + cause);
                return null;
            }, server);
        } catch (PolicyRefusal | IllegalStateException refused) {
            return CompletableFuture.completedFuture(null);
        }
    }

    private static <T> CompletableFuture<T> result(OperationHandle<T> operation) {
        return DocumentActions.result(operation).toCompletableFuture();
    }
    private String read() {
        try { return Files.readString(path); }
        catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
    }
    private void write(String value) {
        try { Files.writeString(path, value); }
        catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
    }
    private void close() {
        editors.forEach(ServerSettings.Editor::close);
        write(original);
        var current = Wayfinder.readPolicy(server.createCommandSourceStack());
        Wayfinder.applyPolicy(server.createCommandSourceStack(), current.revision(), WayfinderSettings.parse(original));
        var remaining = server.getPlayerList().getPlayer(player.getUUID());
        if (remaining != null) server.getPlayerList().remove(remaining);
    }
    private static void check(boolean value, String reason) {
        if (!value) throw new IllegalStateException(reason);
    }
}
