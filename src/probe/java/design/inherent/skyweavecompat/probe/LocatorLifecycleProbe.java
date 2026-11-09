package design.inherent.skyweavecompat.probe;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.commands.*;
import net.minecraft.network.chat.*;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;

/** Real caller removal, dimension travel and shutdown while both native commands are active. */
final class LocatorLifecycleProbe {
    private static final List<Component> messages = new ArrayList<>();
    private static CommandSourceStack console, caller;
    private static ServerPlayer player;
    private static Path output;
    private static JsonObject report;
    private static boolean active, unloading;
    private static int step, ticks, changed;
    private static long began, previousTick, maximumGap;
    private static java.util.concurrent.CompletableFuture<?> background;
    private static final net.minecraft.server.level.TicketType<Integer> BACKGROUND =
            net.minecraft.server.level.TicketType.create("skyweave_probe_background", Integer::compareTo);
    private static net.minecraft.world.level.ChunkPos backgroundChunk;

    private static void releaseBackground(MinecraftServer server) {
        if (backgroundChunk == null) return;
        ((design.inherent.skyweavecompat.mixin.StructureSearchCacheAccess)server.overworld().getChunkSource())
                .skyweave$searchTickets().removeTicket(BACKGROUND, backgroundChunk,
                        net.minecraft.server.level.ChunkLevel.byStatus(net.minecraft.world.level.chunk.status.ChunkStatus.FULL), 0);
        backgroundChunk = null;
    }

    static void start(MinecraftServer server, Path destination, JsonObject evidence, long started, long earlierGap) throws Exception {
        output = destination; report = evidence; ticks = changed = step = 0; active = true;
        began = started; previousTick = System.nanoTime(); maximumGap = earlierGap;
        console = server.createCommandSourceStack().withSource(new CommandSource() {
            public void sendSystemMessage(Component message) { messages.add(message); }
            public boolean acceptsSuccess() { return true; }
            public boolean acceptsFailure() { return true; }
            public boolean shouldInformAdmins() { return false; }
        });
        var level = server.overworld();
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, (LevelEvent.Unload event) -> {
            if (!unloading || event.getLevel() != level) return;
            try {
                // Island cancellation removes its job; structures retains a terminal report.
                // Assert each owner's native contract after its unload listener has run.
                expect(server, "islands", "status", "missing", console);
                expect(server, "structures", "status", "cancelled", console);
                StructureLocatorProbe.assertNoTickets(server, Set.of("skyweave_structure_search", "skyweave_probe_background"));
                report.addProperty("worldUnloadCancelledBothLocators", true);
                report.addProperty("complete", true);
                write(); unloading = false;
            } catch (Throwable failure) { recordFailure(failure); }
        });
        actor(server, "LocatorA");
        beginBoth(server, caller);
        var pos = level.getSharedSpawnPos();
        backgroundChunk = new net.minecraft.world.level.ChunkPos((pos.getX() >> 4) + 32, (pos.getZ() >> 4) + 32);
        ((design.inherent.skyweavecompat.mixin.StructureSearchCacheAccess)level.getChunkSource()).skyweave$searchTickets()
                .addTicket(BACKGROUND, backgroundChunk,
                        net.minecraft.server.level.ChunkLevel.byStatus(net.minecraft.world.level.chunk.status.ChunkStatus.FULL), 0);
        var requested = backgroundChunk;
        background = java.util.concurrent.CompletableFuture.supplyAsync(() -> level.getChunkSource()
                .getChunkFuture(requested.x, requested.z,
                        net.minecraft.world.level.chunk.status.ChunkStatus.FULL, true)).thenCompose(future -> future)
                .thenAccept(chunk -> { if (!chunk.isSuccess()) throw new AssertionError("Background FULL chunk failed: " + chunk.getError()); });
    }
    static void tick(MinecraftServer server) {
        if (!active) return;
        try {
            long now = System.nanoTime(); maximumGap = Math.max(maximumGap, now - previousTick); previousTick = now;
            if (++ticks > 1200) throw new AssertionError("Locator lifecycle fixture exceeded 1200 ticks");
            if (ticks - changed < 3) return;
            if (step == 0) {
                runningBoth(server); server.getPlayerList().remove(player); step = 1;
            } else if (step == 1) {
                beginBoth(server, console); cancelBoth(server);
                report.addProperty("disconnectCancelledBothLocators", true);
                actor(server, "LocatorB"); beginBoth(server, caller); step = 2;
            } else if (step == 2) {
                runningBoth(server);
                if (!player.teleportTo(server.getLevel(Level.END), player.getX(), 100, player.getZ(), Set.of(), 0, 0)
                        || player.serverLevel() == server.overworld()) throw new AssertionError("Native dimension travel failed");
                step = 3;
            } else if (step == 3 && background.isDone()) {
                background.join(); releaseBackground(server); report.addProperty("concurrentFullChunkCompleted", true);
                beginBoth(server, console);
                report.addProperty("dimensionChangeCancelledBothLocators", true);
                server.getPlayerList().remove(player);
                report.addProperty("complete", false);
                unloading = true; active = false; write(); server.halt(false);
            } else return;
            changed = ticks;
        } catch (Throwable failure) { releaseBackground(server); recordFailure(failure); active = false; server.halt(false); }
    }
    private static void actor(MinecraftServer server, String name) {
        var cookie = new CommonListenerCookie(new com.mojang.authlib.GameProfile(UUID.randomUUID(), name), 0,
                net.minecraft.server.level.ClientInformation.createDefault(), false,
                net.neoforged.neoforge.network.connection.ConnectionType.NEOFORGE);
        player = ProbePlayers.login(server, cookie); server.getPlayerList().op(player.getGameProfile());
        if (!player.hasPermissions(2)) throw new AssertionError("Fixture actor is not an operator");
        caller = console.withEntity(player).withPosition(player.position());
    }
    private static void beginBoth(MinecraftServer server, CommandSourceStack source) throws Exception {
        expect(server, "islands", "search 131072", "started", source);
        expect(server, "structures", "search 131072 family=villages", "started", source);
    }
    private static void cancelBoth(MinecraftServer server) throws Exception {
        for (String kind : List.of("islands", "structures")) expect(server, kind, "cancel", "cancelled", console);
        StructureLocatorProbe.assertNoSearchTickets(server);
    }
    private static void runningBoth(MinecraftServer server) throws Exception {
        for (String kind : List.of("islands", "structures")) expect(server, kind, "status", "progress", caller);
    }
    private static void expect(MinecraftServer server, String kind, String command, String expected, CommandSourceStack source) throws Exception {
        messages.clear(); server.getCommands().getDispatcher().execute("skyweavecompat " + kind + " " + command, source);
        if (messages.stream().noneMatch(message -> containsKey(message, "skyweave_compat." + kind + "." + expected)))
            throw new AssertionError(kind + " " + command + " did not report " + expected + ": " + messages);
    }
    private static boolean containsKey(Component message, String key) {
        // sendFailure wraps its translated content in a styled sibling component.
        return message.getContents() instanceof TranslatableContents text && text.getKey().equals(key)
                || message.getSiblings().stream().anyMatch(sibling -> containsKey(sibling, key));
    }
    private static void recordFailure(Throwable failure) {
        report.addProperty("complete", false); report.addProperty("error", failure.toString());
        failure.printStackTrace(); write();
    }
    private static void write() {
        report.addProperty("elapsedSeconds", (System.nanoTime() - began) / 1e9);
        report.addProperty("maximumTickGapMillis", maximumGap / 1e6);
        try { Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n"); }
        catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }
}
