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
    private static long started, lastTick, maxGap, runStarted, firstResult;
    private static int ticks, phase, phaseTick;
    private static boolean active;
    private static String query;
    private static java.util.concurrent.CompletableFuture<JsonObject> planning;

    static void prepare(MinecraftServer server) throws Exception {
        if (System.getProperty("neighbors.probe.searchFixture", "").equals("mixed")) {
            var template = net.minecraft.nbt.TagParser.parseTag("{size:[1,1,1],palette:[{Name:'minecraft:stone'}],blocks:[{pos:[0,0,0],state:0}],entities:[]}");
            template.putInt("DataVersion", net.minecraft.SharedConstants.getCurrentVersion().getDataVersion().getVersion());
            var path = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.GENERATED_DIR)
                    .resolve("skyweave_probe/structures/air_marker.nbt");
            Files.createDirectories(path.getParent()); net.minecraft.nbt.NbtIo.writeCompressed(template, path);
        }
    }

    static void start(MinecraftServer server, Path destination) throws Exception {
        output = destination; ticks = 0; phase = 0; phaseTick = 0; maxGap = 0; planning = null;
        started = lastTick = runStarted = System.nanoTime(); firstResult = -1; messages.clear();
        source = server.createCommandSourceStack().withSource(new CommandSource() {
            public void sendSystemMessage(Component message) { messages.add(message); }
            public boolean acceptsSuccess() { return true; }
            public boolean acceptsFailure() { return true; }
            public boolean shouldInformAdmins() { return false; }
        }).withPosition(net.minecraft.world.phys.Vec3.atCenterOf(server.overworld().getSharedSpawnPos()));
        report.addProperty("originX", source.getPosition().x); report.addProperty("originZ", source.getPosition().z);
        report.addProperty("seed", server.overworld().getSeed());
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
            if (firstResult < 0 && has("result")) firstResult = now - runStarted;
            if (phase <= 1 && (has("complete") || has("partial"))) {
                var terminal = messages.stream().filter(m -> Set.of("complete", "partial").contains(key(m))).findFirst().orElseThrow();
                var values = ((TranslatableContents)terminal.getContents()).getArgs();
                var run = new JsonObject(); run.addProperty("outcome", key(terminal));
                run.addProperty("firstResultMillis", firstResult < 0 ? -1 : firstResult / 1_000_000.0);
                if (((Number)values[1]).intValue() < Integer.getInteger("neighbors.probe.searchMinMatches", 0))
                    throw new AssertionError("Known positive search failed: " + terminal.getString());
                run.addProperty("candidates", ((Number)values[0]).intValue()); run.addProperty("matches", ((Number)values[1]).intValue());
                if (((Number)values[1]).intValue() == 0 && (has("page")
                        || !has(key(terminal).equals("complete") ? "empty_complete" : "empty_partial")))
                    throw new AssertionError("Empty results must explain coverage instead of displaying a blank page");
                var results = pageResults();
                run.add("firstPage", results.deepCopy()); run.addProperty("serverTicks", ticks);
                int pages = Math.ceilDiv(Math.min(64, ((Number)values[1]).intValue()), 10);
                for (int page = 2; page <= pages; page++) {
                    command(server, "page " + page);
                    for (var result : pageResults()) results.add(result);
                }
                run.add("results", results);
                if (System.getProperty("neighbors.probe.searchFixture", "").equals("mixed")) {
                    if (!has("unsupported_entries") || !key(terminal).equals("partial")
                            || results.asList().stream().noneMatch(value -> value.getAsString().contains("skyweave_probe:air_marker"))
                            || results.asList().stream().noneMatch(value -> !value.getAsString().contains("skyweave_probe:air_marker")))
                        throw new AssertionError("Mixed query omitted managed/foreign starts or failed to qualify unsupported placement");
                    run.addProperty("mixedContractsVerified", true);
                }
                if (System.getProperty("neighbors.probe.searchFixture", "").equals("cap")) {
                    if (!key(terminal).equals("partial") || !has("limit_coverage") || ((Number)values[0]).intValue() > 16)
                        throw new AssertionError("Candidate cap did not preserve bounded incomplete coverage");
                    run.addProperty("candidateCapVerified", true);
                }
                command(server, "page");
                if (((Number)values[1]).intValue() > 0 && !has("page"))
                    throw new AssertionError("Page shortcut did not show retained results");
                report.add(phase == 0 ? "cold" : "warm", run);
                if (phase == 1 && System.getProperty("neighbors.probe.searchFixture", "").equals("cap")) {
                    assertNoSearchTickets(server); report.addProperty("leasesReleased", true);
                    report.addProperty("complete", true); finish(server, null); return;
                }
                if (terminal.getString().contains("skyweave_compat.structures.")) throw new AssertionError("Missing readable fallback");
                if (maxGap > java.util.concurrent.TimeUnit.SECONDS.toNanos(5)) throw new AssertionError("Search stalled ordinary server ticks");
                phase++; messages.clear(); phaseTick = ticks;
                if (phase == 1) {
                    runStarted = System.nanoTime(); firstResult = -1; command(server, query);
                }
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
                var completion = messages.stream().filter(m -> key(m).equals("complete")).findFirst().orElseThrow();
                if (((Number)((TranslatableContents)completion.getContents()).getArgs()[1]).intValue() == 0
                        && (!has("empty_complete") || has("page")))
                    throw new AssertionError("Completed empty search displayed a blank results page");
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
                if (System.getProperty("neighbors.probe.searchFixture", "").equals("lifecycle")) {
                    active = false; LocatorLifecycleProbe.start(server, output, report, started, maxGap);
                } else if (Boolean.getBoolean("neighbors.probe.searchPlanning") && !Boolean.getBoolean("neighbors.probe.savedStartsOnly")) {
                    int x = (int)source.getPosition().x, z = (int)source.getPosition().z;
                    planning = java.util.concurrent.CompletableFuture.supplyAsync(() ->
                            System.getProperty("neighbors.probe.searchTarget", "").equals("structure=skyvillages:skyvillage")
                                    ? SkyVillageSearchProbe.verify(server.overworld(), x, z)
                                    : SearchPlanningProbe.verify(server.overworld(), x, z));
                    phase = 4;
                } else { report.addProperty("complete", true); finish(server, null); }
            } else if (phase == 4 && planning.isDone()) {
                report.add("planning", planning.join());
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
    static void assertNoSearchTickets(MinecraftServer server) throws Exception {
        assertNoTickets(server, Set.of("skyweave_structure_search"));
    }
    static void assertNoTickets(MinecraftServer server, Set<String> types) throws Exception {
        var manager = ((design.inherent.skyweavecompat.mixin.StructureSearchCacheAccess)server.overworld().getChunkSource()).skyweave$searchTickets();
        var field = net.minecraft.server.level.DistanceManager.class.getDeclaredField("tickets"); field.setAccessible(true);
        for (var value : ((Map<?, ?>)field.get(manager)).values()) for (var entry : (Iterable<?>)value) {
            var ticket = (net.minecraft.server.level.Ticket<?>)entry;
            if (types.contains(ticket.getType().toString()))
                throw new AssertionError("Search retained a ticket");
        }
    }
    private static void command(MinecraftServer server, String command) throws Exception { command(server, command, source); }
    private static void command(MinecraftServer server, String command, CommandSourceStack origin) throws Exception {
        server.getCommands().getDispatcher().execute("skyweavecompat structures " + command, origin);
    }
    private static boolean has(String value) { return messages.stream().anyMatch(message -> key(message).equals(value)); }
    private static JsonArray pageResults() {
        int header = -1;
        for (int i = 0; i < messages.size(); i++) if (key(messages.get(i)).equals("page")) header = i;
        var results = new JsonArray();
        if (header < 0) return results;
        for (var message : messages.subList(header + 1, messages.size())) if (key(message).equals("result")) {
            var args = ((TranslatableContents)message.getContents()).getArgs(); var coordinate = (Component)args[1];
            if (coordinate.getStyle().getClickEvent() == null
                    || coordinate.getStyle().getClickEvent().getAction() != ClickEvent.Action.SUGGEST_COMMAND
                    || message.getStyle().getHoverEvent() == null) throw new AssertionError("Missing result navigation");
            results.add(message.getString());
        }
        return results;
    }
    private static String key(Component message) {
        if (message.getContents() instanceof TranslatableContents contents)
            return contents.getKey().replace("skyweave_compat.structures.", "");
        return message.getSiblings().stream().map(StructureLocatorProbe::key).filter(value -> !value.isEmpty()).findFirst().orElse("");
    }
    private StructureLocatorProbe() {}
}
