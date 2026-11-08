package design.inherent.skyweavecompat.probe;

import com.google.gson.*;
import net.minecraft.commands.*;
import net.minecraft.network.chat.*;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import java.nio.file.*;
import java.util.*;

/** Executes the real asynchronous command while observing ordinary server ticks and protocol components. */
final class StructureLocatorProbe {
    private static final List<Component> messages = new ArrayList<>();
    private static final JsonObject report = new JsonObject();
    private static CommandSourceStack source;
    private static Path output;
    private static long started, lastTick, maxGap;
    private static int ticks, phase, phaseTick;
    private static boolean active;
    private static String query;

    static void start(MinecraftServer server, Path destination) throws Exception {
        output = destination; ticks = 0; phase = 0; phaseTick = 0; maxGap = 0;
        started = lastTick = System.nanoTime(); messages.clear();
        source = server.createCommandSourceStack().withSource(new CommandSource() {
            public void sendSystemMessage(Component message) { messages.add(message); }
            public boolean acceptsSuccess() { return true; }
            public boolean acceptsFailure() { return true; }
            public boolean shouldInformAdmins() { return false; }
        }).withPosition(net.minecraft.world.phys.Vec3.atCenterOf(server.overworld().getSharedSpawnPos()));
        try {
            command(server, "search 100 family=villages", source.withPermission(0));
            throw new AssertionError("Unprivileged structure search accepted");
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) { }
        query = "search " + Integer.getInteger("neighbors.probe.searchRadius", 12800) + " "
                + System.getProperty("neighbors.probe.searchTarget", "structure=ctov:medium/village_plains");
        command(server, query); active = true;
        if (!has("started")) throw new AssertionError("Search did not accept its target: " + messages);
    }
    static void tick(MinecraftServer server) {
        if (!active) return;
        try {
            long now = System.nanoTime(); maxGap = Math.max(maxGap, now - lastTick); lastTick = now; ticks++;
            if (now - started > java.util.concurrent.TimeUnit.SECONDS.toNanos(400)) throw new AssertionError("Search fixture exceeded its bounded duration");
            if (has("failed")) throw new AssertionError("Search failed: " + messages);
            if (phase <= 1 && (has("complete") || has("partial"))) {
                var terminal = messages.stream().filter(m -> Set.of("complete", "partial").contains(key(m))).findFirst().orElseThrow();
                var values = ((TranslatableContents)terminal.getContents()).getArgs();
                var run = new JsonObject(); run.addProperty("outcome", key(terminal));
                run.addProperty("candidates", ((Number)values[0]).intValue()); run.addProperty("matches", ((Number)values[1]).intValue());
                var results = new JsonArray();
                for (var message : messages) if (key(message).equals("result")) {
                    var args = ((TranslatableContents)message.getContents()).getArgs();
                    var coordinate = (Component)args[1];
                    if (coordinate.getStyle().getClickEvent() == null
                            || coordinate.getStyle().getClickEvent().getAction() != ClickEvent.Action.SUGGEST_COMMAND
                            || message.getStyle().getHoverEvent() == null) throw new AssertionError("Missing result navigation");
                    results.add(message.getString());
                }
                run.add("firstPage", results); run.addProperty("serverTicks", ticks);
                report.add(phase == 0 ? "cold" : "warm", run);
                if (terminal.getString().contains("skyweave_compat.structures.")) throw new AssertionError("Missing readable fallback");
                if (maxGap > java.util.concurrent.TimeUnit.SECONDS.toNanos(5)) throw new AssertionError("Search stalled ordinary server ticks");
                phase++; messages.clear(); phaseTick = ticks;
                if (phase == 1) command(server, query);
                else command(server, "search 131072 family=villages");
            } else if (phase == 2 && ticks - phaseTick >= 40) {
                command(server, "cancel");
                if (!has("cancelled")) throw new AssertionError("Cancellation missing");
                assertNoSearchTickets(server);
                for (int retry = 0; retry < 3; retry++) {
                    command(server, "search 131072 family=villages");
                    command(server, "cancel");
                    assertNoSearchTickets(server);
                }
                messages.clear(); command(server, "search 1 family=villages"); phase = 3; phaseTick = ticks;
            } else if (phase == 3 && has("complete") && ticks - phaseTick >= 20) {
                long completions = messages.stream().filter(m -> key(m).equals("complete")).count();
                if (completions != 1) throw new AssertionError("Cancelled callback published stale completion");
                messages.clear(); command(server, "search 100 structure=minecraft:stronghold");
                if (!has("invalid")) throw new AssertionError("Unsupported concentric placement was not qualified");
                messages.clear(); command(server, "search 100 structure=minecraft:not_registered");
                if (!has("invalid")) throw new AssertionError("Unknown structure accepted");
                report.addProperty("cancelledCallbackIsolated", true); report.addProperty("permissionDenied", true);
                assertNoSearchTickets(server); report.addProperty("leasesReleased", true);
                if (Boolean.getBoolean("neighbors.probe.savedStartsOnly")) {
                    if (!Boolean.getBoolean("neighbors.probe.savedStartsExercised")) throw new AssertionError("Saved-start discriminator was not exercised");
                    report.addProperty("forcedNewStartRejection", true);
                }
                report.addProperty("complete", true); finish(server, null);
            }
        } catch (Throwable failure) { finish(server, failure); }
    }
    private static void finish(MinecraftServer server, Throwable failure) {
        active = false;
        report.addProperty("elapsedSeconds", (System.nanoTime() - started) / 1e9);
        report.addProperty("maximumTickGapMillis", maxGap / 1e6);
        if (failure != null) { report.addProperty("complete", false); report.addProperty("error", failure.toString()); failure.printStackTrace(); }
        try { Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n"); }
        catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
        server.halt(false);
    }
    private static void assertNoSearchTickets(MinecraftServer server) throws Exception {
        var manager = ((design.inherent.skyweavecompat.mixin.StructureSearchCacheAccess)server.overworld().getChunkSource()).skyweave$searchTickets();
        var field = net.minecraft.server.level.DistanceManager.class.getDeclaredField("tickets"); field.setAccessible(true);
        for (var value : ((Map<?, ?>)field.get(manager)).values()) for (var entry : (Iterable<?>)value) {
            var ticket = (net.minecraft.server.level.Ticket<?>)entry;
            if (ticket.getType().toString().equals("skyweave_structure_search")) throw new AssertionError("Search retained a ticket");
        }
    }
    private static void command(MinecraftServer server, String command) throws Exception { command(server, command, source); }
    private static void command(MinecraftServer server, String command, CommandSourceStack origin) throws Exception {
        server.getCommands().getDispatcher().execute("skyweavecompat structures " + command, origin);
    }
    private static boolean has(String value) { return messages.stream().anyMatch(message -> key(message).equals(value)); }
    private static String key(Component message) {
        if (message.getContents() instanceof TranslatableContents contents)
            return contents.getKey().replace("skyweave_compat.structures.", "");
        return message.getSiblings().stream().map(StructureLocatorProbe::key).filter(value -> !value.isEmpty()).findFirst().orElse("");
    }
    private StructureLocatorProbe() {}
}
