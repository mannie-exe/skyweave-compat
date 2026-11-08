package design.inherent.skyweavecompat.probe;

import com.google.gson.*;
import net.minecraft.commands.CommandSource;
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
    private static Path destination;
    private static int before, maximumLoaded, phase, ticks;
    private static long began;
    private static boolean active;
    private static final JsonObject report = new JsonObject();

    static void start(MinecraftServer server, Path output) throws Exception {
        destination = output; messages.clear(); phase = 0; ticks = 0;
        CommandSource sink = new CommandSource() {
            public void sendSystemMessage(Component message) { messages.add(message); }
            public boolean acceptsSuccess() { return true; }
            public boolean acceptsFailure() { return true; }
            public boolean shouldInformAdmins() { return false; }
        };
        source = server.createCommandSourceStack().withSource(sink).withPosition(new Vec3(.5, 200, .5));
        maximumLoaded = before = server.overworld().getChunkSource().getLoadedChunksCount(); began = System.nanoTime();
        try {
            command(server, "search 8000", source.withPermission(0));
            throw new AssertionError("Unauthorized search accepted");
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) { }
        // More than one worker slice, including any slow cold-cache slice.
        command(server, "search 24000 family=any");
        active = true;
    }

    static void tick(MinecraftServer server) {
        if (!active) return;
        try {
            if (++ticks > 2400) throw new AssertionError("Metadata command exceeded 120 seconds");
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
                long results = messages.stream().filter(m -> key(m).equals("result")).count();
                if (results < 1 || results > 10) throw new AssertionError("Missing results or unsolicited repeated pages");
                var first = messages.stream().filter(m -> key(m).equals("result")).findFirst().orElseThrow();
                var coordinate = (Component)((TranslatableContents)first.getContents()).getArgs()[0];
                if (coordinate.getStyle().getClickEvent() == null
                        || coordinate.getStyle().getClickEvent().getAction() != ClickEvent.Action.SUGGEST_COMMAND
                        || first.getStyle().getHoverEvent() == null) throw new AssertionError("Result click/hover contract missing");
                if (completion.getString().contains("skyweave_compat.islands.")) throw new AssertionError("Unreadable language fallback");
                if (!hasAction(ClickEvent.Action.RUN_COMMAND, "page 2")) throw new AssertionError("Next page action missing");
                messages.clear(); command(server, "page 2");
                if (!hasAction(ClickEvent.Action.RUN_COMMAND, "page 1")) throw new AssertionError("Previous page action missing");
                command(server, "page 7");
                if (has("page_missing")) throw new AssertionError("Retained final page unavailable");
                command(server, "search 1000 archetype=classic edge=coastal");
                if (!has("invalid")) throw new AssertionError("Conflicting filters accepted");
                report.addProperty("firstPageMatches", results);
                report.addProperty("automaticCells", (int)args[0]);
                messages.clear(); command(server, "search 48000 biome=minecraft:the_void");
                command(server, "page 1");
                if (!has("page_provisional")) throw new AssertionError("Running cursor cannot be paged");
                command(server, "more");
                if (!has("progress")) throw new AssertionError("Legacy more alias is not an observational status");
                phase = 1;
            } else if (phase == 1) {
                messages.clear(); command(server, "status");
                var progress = messages.stream().filter(m -> key(m).equals("progress")).findFirst().orElseThrow();
                int visited = (int)((TranslatableContents)progress.getContents()).getArgs()[0];
                if (visited == 0) return;
                if (visited >= (int)((TranslatableContents)progress.getContents()).getArgs()[1])
                    throw new AssertionError("Cancellation case already complete");
                command(server, "cancel");
                if (!has("cancelled")) throw new AssertionError("Cancellation not acknowledged");
                messages.clear(); command(server, "more");
                if (!has("missing")) throw new AssertionError("Cancelled cursor remained active");
                messages.clear(); command(server, "search 1 biome=minecraft:the_void"); phase = 2;
            } else if (phase == 2) {
                if (!has("complete")) return;
                if (!has("no_match")) throw new AssertionError("Completed absence not reported");
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
                report.addProperty("permissionDenied", true); report.addProperty("cancelledCursorRejected", true);
                report.addProperty("automaticContinuation", true); report.addProperty("completedNoMatch", true);
                report.addProperty("clickableResultsAndPages", true); report.addProperty("provisionalPaging", true);
                report.addProperty("elapsedSeconds", (System.nanoTime() - began) / 1e9);
                Files.writeString(destination, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
                active = false; server.halt(false);
            }
        } catch (Throwable error) {
            error.printStackTrace(); active = false; server.halt(false);
        }
    }
    private static boolean has(String key) { return messages.stream().anyMatch(m -> key(m).equals(key)); }
    private static boolean hasAction(ClickEvent.Action action, String suffix) {
        return messages.stream().flatMap(m -> m.toFlatList().stream()).anyMatch(m -> {
            var click = m.getStyle().getClickEvent();
            return click != null && click.getAction() == action && click.getValue().endsWith(suffix);
        });
    }
    private static void command(MinecraftServer server, String text) throws Exception { command(server, text, source); }
    private static void command(MinecraftServer server, String text, CommandSourceStack origin) throws Exception {
        server.getCommands().getDispatcher().execute("skyweavecompat islands " + text, origin);
    }
    private static String key(Component message) {
        if (message.getContents() instanceof TranslatableContents contents)
            return contents.getKey().replace("skyweave_compat.islands.", "");
        // CommandSourceStack wraps failure messages in its red-styled parent.
        return message.getSiblings().stream().map(LocatorProbe::key).filter(key -> !key.isEmpty()).findFirst().orElse("");
    }
    private LocatorProbe() {}
}
