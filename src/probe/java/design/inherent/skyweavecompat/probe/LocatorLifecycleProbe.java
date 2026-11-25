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

/** Real respawn, caller removal, dimension travel and shutdown under active native commands. */
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
    private static String structuresOutcome;
    private static design.inherent.reform.minecraft.command.SearchRecipient capturedRecipient;
    private static net.minecraft.world.phys.Vec3 capturedOrigin;

    private static void releaseBackground(MinecraftServer server) {
        if (backgroundChunk == null) return;
        ((design.inherent.skyweavecompat.mixin.StructureSearchCacheAccess)server.overworld().getChunkSource())
                .skyweave$searchTickets().removeTicket(BACKGROUND, backgroundChunk,
                        net.minecraft.server.level.ChunkLevel.byStatus(net.minecraft.world.level.chunk.status.ChunkStatus.FULL), 0);
        backgroundChunk = null;
    }

    static void start(MinecraftServer server, Path destination, JsonObject evidence, long started, long earlierGap) throws Exception {
        output = destination; report = evidence; ticks = changed = step = 0; active = true; structuresOutcome = null;
        server.setDifficulty(net.minecraft.world.Difficulty.PEACEFUL, true);
        began = started; previousTick = System.nanoTime(); maximumGap = earlierGap;
        console = server.createCommandSourceStack();
        var level = server.overworld();
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, (LevelEvent.Unload event) -> {
            if (!unloading || event.getLevel() != level) return;
            try {
                for (String kind : List.of("islands", "structures"))
                    if (consoleCommand(server, kind, "status") != 0)
                        throw new AssertionError(kind + " exposed results after its native world retired");
                StructureLocatorProbe.assertNoTickets(server, Set.of("skyweave_structure_search", "skyweave_probe_background"));
                report.addProperty("worldUnloadCancelledBothLocators", true);
                report.addProperty("complete", true);
                write(); unloading = false;
            } catch (Throwable failure) { recordFailure(failure); }
        });
        actor(server, "LocatorA");
        capturedRecipient = design.inherent.reform.minecraft.command.SearchRecipient.capture(caller, 2);
        capturedOrigin = capturedRecipient.origin();
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
                runningBoth(server);
                if (!player.teleportTo(player.serverLevel(), player.getX() + 16, player.getY(), player.getZ() + 16,
                        Set.of(), player.getYRot(), player.getXRot())) throw new AssertionError("Native same-dimension teleport failed");
                caller = player.createCommandSourceStack();
                if (caller.getPosition().equals(capturedOrigin)) throw new AssertionError("Teleport did not move the actor");
                step = 9;
            } else if (step == 9) {
                runningBoth(server);
                if (!capturedRecipient.origin().equals(capturedOrigin)) throw new AssertionError("Teleport rewrote the captured search origin");
                report.addProperty("sameDimensionTeleportRetainsBothSearches", true);
                report.addProperty("capturedOriginStableAfterTeleport", true);
                runningBoth(server); respawn(server); runningBoth(server);
                if (!capturedRecipient.origin().equals(capturedOrigin)) throw new AssertionError("Respawn rewrote the captured search origin");
                report.addProperty("capturedOriginStableAfterRespawn", true);
                report.addProperty("sameConnectionRespawnRetainsBothSearches", true); step = 1;
            } else if (step == 1) {
                runningBoth(server); cancelBothAsPlayer(server);
                step = 6;
            } else if (step == 6) {
                if (!reportStatus(server, "islands", "cancelled") || !StructureLocatorProbe.retired(server)) return;
                expect(server,"islands","search 1","started",caller);
                expect(server,"structures","search 1 family=villages","started",caller);step=2;
            } else if (step == 2) {
                if (!terminal(server,"islands") || !terminal(server,"structures")) return;
                respawn(server);
                expect(server,"islands","status","report.zero",caller);
                expect(server,"structures","status","report."+structuresOutcome,caller);
                expect(server,"islands","page","report.zero",caller);
                expect(server,"structures","page","report."+structuresOutcome,caller);
                report.addProperty("completedStructuresOutcome",structuresOutcome);
                report.addProperty("completedResultsAccessibleAfterRespawn",true);
                beginBoth(server,caller);server.getPlayerList().remove(player);step=3;
            } else if (step == 3) {
                if (!StructureLocatorProbe.retired(server) || consoleCommand(server, "islands", "search 131072") == 0) return;
                if (consoleCommand(server, "structures", "search 131072 family=villages") == 0)
                    throw new AssertionError("Disconnected structure owner retained admission");
                cancelBoth(server);
                report.addProperty("disconnectCancelledBothLocators", true);
                step = 7;
            } else if (step == 7) {
                actor(server, "LocatorB");
                // A different connection cannot read the old console report.
                expect(server, "islands", "status", "report.unavailable", caller);
                step = 8;
            } else if (step == 8) {
                if (!StructureLocatorProbe.retired(server) || nativeCommand(server, "islands", "search 131072", caller) == 0) return;
                expect(server, "structures", "search 131072 family=villages", "started", caller);
                step = 4;
            } else if (step == 4) {
                runningBoth(server);
                if (!player.teleportTo(server.getLevel(Level.END), player.getX(), 100, player.getZ(), Set.of(), 0, 0)
                        || player.serverLevel() == server.overworld()) throw new AssertionError("Native dimension travel failed");
                step = 5;
            } else if (step == 5 && background.isDone()) {
                if (!StructureLocatorProbe.retired(server) || consoleCommand(server, "islands", "search 131072") == 0) return;
                if (consoleCommand(server, "structures", "search 131072 family=villages") == 0)
                    throw new AssertionError("Structure owner retained admission in a new dimension");
                background.join(); releaseBackground(server); report.addProperty("concurrentFullChunkCompleted", true);
                report.addProperty("dimensionChangeCancelledBothLocators", true);
                server.getPlayerList().remove(player);
                report.addProperty("complete", false);
                unloading = true; active = false; write(); server.halt(false);
            } else return;
            changed = ticks;
        } catch (Throwable failure) { releaseBackground(server); recordFailure(failure); active = false; server.halt(false); }
    }
    private static void respawn(MinecraftServer server) {
        var old=player;var connection=old.connection;
        old.setHealth(0);
        connection.handleClientCommand(new net.minecraft.network.protocol.game.ServerboundClientCommandPacket(
                net.minecraft.network.protocol.game.ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
        player=connection.player;
        if(player==old || player.connection!=connection || connection.player!=player
                || player.serverLevel()!=server.overworld())throw new AssertionError("Fixture did not perform a same-session native respawn");
        caller=player.createCommandSourceStack();
    }
    private static boolean terminal(MinecraftServer server,String kind) throws Exception {
        messages.clear();drainPlayer();messages.clear();
        server.getCommands().getDispatcher().execute("skyweavecompat "+kind+" status",caller);drainPlayer();
        if(kind.equals("structures")) {
            for(var outcome:List.of("complete", "zero", "partial"))if(messages.stream().anyMatch(m -> containsKey(m,"reform.search."+outcome))) {
                structuresOutcome=outcome;return true;
            }
            return false;
        }
        return messages.stream().anyMatch(m -> containsKey(m,"reform.search.zero"));
    }
    private static void cancelBothAsPlayer(MinecraftServer server) throws Exception {
        for (String kind : List.of("islands", "structures")) {
            drainPlayer(); messages.clear();
            nativeCommand(server, kind, "cancel", caller);
            drainPlayer();
            if (messages.stream().noneMatch(m -> containsKey(m, "reform.search.cancelled")
                    || containsKey(m, "reform.search.stopping")))
                throw new AssertionError("Missing native cancellation acknowledgement for " + kind);
        }
        StructureLocatorProbe.assertNoSearchTickets(server);
    }
    private static void drainPlayer() {
        if(player==null)return;
        ProbePlayers.drainChat(player, messages);
    }
    private static void actor(MinecraftServer server, String name) {
        var cookie = new CommonListenerCookie(new com.mojang.authlib.GameProfile(UUID.randomUUID(), name), 0,
                net.minecraft.server.level.ClientInformation.createDefault(), false,
                net.neoforged.neoforge.network.connection.ConnectionType.NEOFORGE);
        player = ProbePlayers.login(server, cookie); server.getPlayerList().op(player.getGameProfile());
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        if (!player.hasPermissions(2)) throw new AssertionError("Fixture actor is not an operator");
        caller = player.createCommandSourceStack();
    }
    private static void beginBoth(MinecraftServer server, CommandSourceStack source) throws Exception {
        expect(server, "islands", "search 131072", "started", source);
        expect(server, "structures", "search 131072 family=villages", "started", source);
    }
    private static void cancelBoth(MinecraftServer server) throws Exception {
        for (String kind : List.of("islands", "structures"))
            if (consoleCommand(server, kind, "cancel") != 1) throw new AssertionError("Console could not cancel " + kind);
        StructureLocatorProbe.assertNoSearchTickets(server);
    }
    private static void runningBoth(MinecraftServer server) throws Exception {
        for (String kind : List.of("islands", "structures")) expect(server, kind, "status", "report.running", caller);
    }
    private static void expect(MinecraftServer server, String kind, String command, String expected, CommandSourceStack source) throws Exception {
        drainPlayer(); messages.clear(); nativeCommand(server, kind, command, source); drainPlayer();
        String key = expected.startsWith("report.") ? "reform.search." + expected.substring(7)
                : "skyweave_compat." + kind + "." + expected;
        if (messages.stream().noneMatch(message -> containsKey(message, key)))
            throw new AssertionError(kind + " " + command + " did not report " + expected + ": " + messages);
    }
    private static boolean reportStatus(MinecraftServer server, String kind, String key) throws Exception {
        drainPlayer(); messages.clear(); nativeCommand(server, kind, "status", caller); drainPlayer();
        return messages.stream().anyMatch(message -> containsKey(message, "reform.search." + key));
    }
    private static int consoleCommand(MinecraftServer server, String kind, String command) throws Exception {
        return nativeCommand(server, kind, command, console);
    }
    private static int nativeCommand(MinecraftServer server, String kind, String command, CommandSourceStack source) throws Exception {
        return server.getCommands().getDispatcher().execute("skyweavecompat " + kind + " " + command, source);
    }
    private static boolean containsKey(Component message, String key) {
        // sendFailure wraps its translated content in a styled sibling component.
        return message.getContents() instanceof TranslatableContents text && text.getKey().equals(key)
                || message.getSiblings().stream().anyMatch(sibling -> containsKey(sibling, key));
    }
    private static void recordFailure(Throwable failure) {
        report.addProperty("complete", false); report.addProperty("error", failure.toString());
        report.addProperty("lifecycleStep",step);report.addProperty("lifecycleTicks",ticks);
        report.addProperty("backgroundDone",background!=null&&background.isDone());
        failure.printStackTrace(); write();
    }
    private static void write() {
        report.addProperty("elapsedSeconds", (System.nanoTime() - began) / 1e9);
        report.addProperty("maximumTickGapMillis", maximumGap / 1e6);
        try { Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n"); }
        catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }
}
