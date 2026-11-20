package design.inherent.skyweavecompat.probe;

import com.google.gson.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.Vec3;
import java.nio.file.*;
import java.util.*;

/** Actual command execution across normal ticks; never pumps or waits on the server event loop. */
final class LocatorProbe {
    private static final List<Component> messages = new ArrayList<>();
    private static CommandSourceStack source;
    private static net.minecraft.server.level.ServerPlayer player;
    private static Path destination;
    private static int before, maximumLoaded, phase, ticks, stableTicks;
    private static long began;
    private static boolean active;
    private static String previousCursor;
    private static final JsonObject report = new JsonObject();

    static void start(MinecraftServer server, Path output) throws Exception {
        destination = output; messages.clear(); phase = -1; ticks = 0; stableTicks = 0;
        player = ProbePlayers.stationary(server, "IslandReport", server.overworld().getSharedSpawnPos().above(3));
        server.getPlayerList().op(player.getGameProfile());
        source = player.createCommandSourceStack().withPosition(new Vec3(.5, 200, .5));
        maximumLoaded = before = server.overworld().getChunkSource().getLoadedChunksCount(); began = System.nanoTime();
        try {
            command(server, "search 8000", source.withPermission(0));
            throw new AssertionError("Unauthorized search accepted");
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) { }
        // Observe settled startup before testing a no-additional-chunks invariant.
        active = true;
    }

    static void tick(MinecraftServer server) {
        if (!active) return;
        try {
            if (++ticks > 2400) throw new AssertionError("Metadata command exceeded 120 seconds");
            ProbePlayers.drainChat(player, messages);
            if (phase == -1) {
                int loaded = server.overworld().getChunkSource().getLoadedChunksCount();
                if (loaded != before) { before = loaded; stableTicks = 0; }
                else stableTicks++;
                if (stableTicks < 40) return;
                maximumLoaded = before; phase = 0;
                report.addProperty("startupStableTicks", stableTicks);
                command(server, "search 131072 family=any");
                return;
            }
            maximumLoaded = Math.max(maximumLoaded, server.overworld().getChunkSource().getLoadedChunksCount());
            if (has("failed")) throw new AssertionError(messages.toString());
            if (phase == 0) {
                if (!has("complete")) return;
                var completion = messages.stream().filter(m -> key(m).equals("complete")).findFirst().orElseThrow();
                Object[] args = ((TranslatableContents)completion.getContents()).getArgs();
                if ((int)args[0] <= 256) throw new AssertionError("Continuation case fit in one slice");
                if (!args[0].equals(args[1])) throw new AssertionError("Search did not visit its full bounds");
                if (messages.stream().filter(m -> key(m).equals("complete")).count() != 1)
                    throw new AssertionError("Repeated completion notice");
                int notices = (int) messages.stream().filter(m -> Set.of("report.notice", "report.notice_one").contains(key(m))).count();
                if (notices < 2) throw new AssertionError("Later real arrivals were not announced: " + notices);
                if (has("result")) throw new AssertionError("Unrequested full pages were sent");
                report.addProperty("coalescedMatchNotices", notices);
                messages.clear(); command(server, "page");
                long results = messages.stream().filter(m -> key(m).equals("result")).count();
                if (results < 1 || results > 10) throw new AssertionError("Missing results or unsolicited repeated pages");
                var first = messages.stream().filter(m -> key(m).equals("result")).findFirst().orElseThrow();
                var coordinate = (Component)((TranslatableContents)first.getContents()).getArgs()[0];
                if (coordinate.getStyle().getClickEvent() == null
                        || coordinate.getStyle().getClickEvent().getAction() != ClickEvent.Action.SUGGEST_COMMAND
                        || first.getStyle().getHoverEvent() == null) throw new AssertionError("Result click/hover contract missing");
                if (completion.getString().contains("skyweave_compat.islands.")) throw new AssertionError("Unreadable language fallback");
                previousCursor = exactPageAction(2);
                messages.clear(); exact(server, previousCursor);
                if (!has("result")) throw new AssertionError("Exact page did not return the retained findings");
                if (exactPageAction(1).equals(previousCursor)) throw new AssertionError("Previous page did not address page one");
                command(server, "page 7");
                if (has("report.page_unavailable")) throw new AssertionError("Retained final page unavailable");
                command(server, "search 1000 archetype=classic shoreline=coastal");
                if (!has("invalid")) throw new AssertionError("Conflicting filters accepted");
                report.addProperty("firstPageMatches", results);
                report.addProperty("automaticCells", (int)args[0]);
                messages.clear(); command(server, "search 48000 biome=minecraft:the_void");
                exact(server, previousCursor);
                if (!has("report.unavailable") || has("result"))
                    throw new AssertionError("An earlier page link retargeted the replacement job");
                messages.clear();
                command(server, "page 1");
                if (!has("report.running") || has("report.zero") || has("result"))
                    throw new AssertionError("Running empty search was presented as complete");
                phase = 1;
            } else if (phase == 1) {
                messages.clear(); command(server, "status");
                var progress = messages.stream().flatMap(LocatorProbe::components)
                        .filter(m -> m.getContents() instanceof TranslatableContents text
                                && text.getKey().equals("reform.search.known"))
                        .findFirst().orElseThrow(() -> new AssertionError("Missing retained progress counts: " + messages));
                var counts = ((TranslatableContents)progress.getContents()).getArgs();
                int visited = ((Number)counts[0]).intValue();
                if (visited == 0) return;
                if (visited >= ((Number)counts[1]).intValue())
                    throw new AssertionError("Cancellation case already complete");
                command(server, "cancel");
                if (!has("report.cancelled") && !has("report.stopping"))
                    throw new AssertionError("Cancellation not acknowledged");
                phase = 2;
            } else if (phase == 2) {
                messages.clear(); command(server, "status");
                if (!has("report.cancelled")) return;
                if (has("report.drain") || has("report.drain_one"))
                    throw new AssertionError("Worker still outstanding at terminal island cancellation");
                messages.clear(); command(server, "search 1 biome=minecraft:the_void"); phase = 3;
            } else if (phase == 3) {
                if (!has("complete")) return;
                command(server, "page");
                if (!has("report.zero") || has("result")) throw new AssertionError("Completed absence not reported");
                if (messages.stream().filter(m -> key(m).equals("complete")).count() != 1)
                    throw new AssertionError("Cancelled stale callback published a completion");
                // Ordinary ticks may unload startup chunks. The observer must not add any.
                if (maximumLoaded > before)
                    throw new AssertionError("Metadata search added loaded chunks: " + before + " -> " + maximumLoaded);
                command(server, "cancel");
                report.addProperty("complete", true);
                report.addProperty("loadedChunksBefore", before);
                report.addProperty("maximumLoadedChunks", maximumLoaded);
                report.addProperty("loadedChunksAfter", server.overworld().getChunkSource().getLoadedChunksCount());
                report.addProperty("permissionDenied", true); report.addProperty("replacedCursorRejected", true);
                report.addProperty("nativeCancellationDrained", true);
                report.addProperty("automaticContinuation", true); report.addProperty("completedNoMatch", true);
                report.addProperty("clickableResultsAndPages", true); report.addProperty("provisionalPaging", true);
                report.addProperty("elapsedSeconds", (System.nanoTime() - began) / 1e9);
                Files.writeString(destination, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
                active = false; server.halt(false);
            }
        } catch (Throwable error) {
            report.addProperty("complete", false);
            report.addProperty("error", error.toString());
            try { Files.writeString(destination, report.toString()); }
            catch (java.io.IOException reporting) { error.addSuppressed(reporting); }
            error.printStackTrace(); active = false; server.halt(false);
        }
    }
    private static boolean has(String key) { return messages.stream().anyMatch(m -> key(m).equals(key)); }
    private static java.util.stream.Stream<Component> components(Component message) {
        return java.util.stream.Stream.concat(java.util.stream.Stream.of(message),
                message.getSiblings().stream().flatMap(LocatorProbe::components));
    }
    private static String exactPageAction(int page) {
        return messages.stream().flatMap(m -> m.toFlatList().stream()).map(m -> m.getStyle().getClickEvent())
                .filter(Objects::nonNull)
                .filter(click -> click.getAction() == ClickEvent.Action.RUN_COMMAND)
                .map(ClickEvent::getValue)
                .filter(value -> value.startsWith("/skyweavecompat islands page at ") && value.endsWith(" " + page))
                .findFirst().orElseThrow(() -> new AssertionError("Exact page " + page + " action missing")).substring(1);
    }
    private static void command(MinecraftServer server, String text) throws Exception { command(server, text, source); }
    private static void command(MinecraftServer server, String text, CommandSourceStack origin) throws Exception {
        server.getCommands().getDispatcher().execute("skyweavecompat islands " + text, origin);
        ProbePlayers.drainChat(player, messages);
    }
    private static void exact(MinecraftServer server, String command) throws Exception {
        server.getCommands().getDispatcher().execute(command, source);
        ProbePlayers.drainChat(player, messages);
    }
    private static String key(Component message) {
        if (message.getContents() instanceof TranslatableContents contents)
            return contents.getKey().replace("skyweave_compat.islands.", "").replace("reform.search.", "report.");
        // CommandSourceStack wraps failure messages in its red-styled parent.
        return message.getSiblings().stream().map(LocatorProbe::key).filter(key -> !key.isEmpty()).findFirst().orElse("");
    }
    private LocatorProbe() {}
}
