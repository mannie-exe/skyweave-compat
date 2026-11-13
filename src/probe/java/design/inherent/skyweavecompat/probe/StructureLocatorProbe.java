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
    private static long started, lastTick, maxGap, runStarted, firstResult, nextSnapshot;
    private static int ticks, phase, phaseTick;
    private static boolean active;
    private static String query;
    private static java.util.concurrent.CompletableFuture<JsonObject> planning;
    private static boolean streamedVerification;
    private static boolean resultWhilePlanning;
    private static int selector;
    private static String familyQuery, selectedId;
    private static JsonObject selectorRuns;
    private static boolean focusPending;
    private static int queryRadius;

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
        if (System.getProperty("neighbors.probe.searchFixture", "").equals("policy")) { LocatorPolicyProbe.start(server,destination); return; }
        output = destination; ticks = 0; phase = 0; phaseTick = 0; maxGap = 0; planning = null;
        started = lastTick = runStarted = System.nanoTime(); firstResult = -1; streamedVerification=false; resultWhilePlanning=false; messages.clear();
        selector=0;selectorRuns=new JsonObject();selectedId=null;
        focusPending=System.getProperty("neighbors.probe.searchFixture", "").equals("selectors");
        queryRadius=Integer.getInteger("neighbors.probe.searchRadius",12800);
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
        try {
            server.getCommands().getDispatcher().execute("skyweavecompat islands search 100", source.withPermission(0));
            throw new AssertionError("Unprivileged island search accepted");
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) { }
        query = "search " + Integer.getInteger("neighbors.probe.searchRadius", 12800) + " "
                + System.getProperty("neighbors.probe.searchTarget", "structure=ctov:medium/village_plains");
        familyQuery=System.getProperty("neighbors.probe.searchTarget", "");
        if(System.getProperty("neighbors.probe.searchFixture", "").equals("selectors")
                && !Set.of("family=villages","family=desert_temples","family=jungle_temples").contains(familyQuery))
            throw new IllegalArgumentException("Selector fixture requires a village or temple family");
        if(System.getProperty("neighbors.probe.searchFixture", "").equals("time")
                && design.inherent.skyweavecompat.terrain.structure.StructureLocator.readPolicy(source).value().timeLimitSeconds()>10)
            throw new IllegalArgumentException("Time-exhaustion fixture requires a5–10second policy");
        report.addProperty("complete",false);report.addProperty("fixturePhase","admitting first request");
        writeSnapshot();
        long before=System.nanoTime();command(server, query);long after=System.nanoTime();active = true;
        if (!has("started")) throw new AssertionError("Search did not accept its target: " + messages);
        var job=acceptedJob(server);
        var limits=(design.inherent.skyweavecompat.terrain.structure.SearchSettings)field(job,"limits");
        long deadline=(Long)field(job,"deadline");
        long allowance=java.util.concurrent.TimeUnit.SECONDS.toNanos(limits.timeLimitSeconds());
        if(deadline<before+allowance || deadline>after+allowance)
            throw new AssertionError("Accepted request deadline does not use its operational settings");
        report.add("acceptedSearchSettings",new Gson().toJsonTree(limits));
        report.addProperty("actualRequestDeadlineUsesSettings",true);
        nextSnapshot=after;
    }
    private static void writeSnapshot() throws java.io.IOException {
        Files.writeString(output,new GsonBuilder().setPrettyPrinting().create().toJson(report)+"\n");
    }
    private static Object field(Object owner,String name) throws Exception {
        var field=owner.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(owner);
    }
    private static Object acceptedJob(MinecraftServer server) throws Exception {
        var command=server.getCommands().getDispatcher().getRoot().getChild("skyweavecompat")
                .getChild("structures").getChild("search").getChild("radius").getChild("target").getCommand();
        for(var captured:command.getClass().getDeclaredFields()) {
            if(captured.getType()!=design.inherent.skyweavecompat.terrain.structure.StructureLocator.class)continue;
            captured.setAccessible(true);
            var states=(Map<?,?>)field(captured.get(command),"servers");
            var state=states.get(server);
            if(state!=null)return field(state,"job");
        }
        throw new AssertionError("Cannot inspect the accepted native locator request");
    }
    static void tick(MinecraftServer server) {
        LocatorPolicyProbe.tick(server);
        if (!active) return;
        try {
            long now = System.nanoTime(); maxGap = Math.max(maxGap, now - lastTick); lastTick = now; ticks++;
            if(now>=nextSnapshot) {
                nextSnapshot=now+java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
                if(!has("complete") && !has("partial") && !has("failed") && !has("cancelled"))command(server,"status");
                report.addProperty("fixturePhase",phase);report.addProperty("activeQuery",query);
                report.addProperty("elapsedSeconds",(now-started)/1e9);
                report.addProperty("maximumTickGapMillis",maxGap/1e6);
                for(var message:messages)if(key(message).equals("pipeline")) {
                    var args=((TranslatableContents)message.getContents()).getArgs();
                    var pipeline=new JsonObject();
                    pipeline.addProperty("loaded",((Number)args[0]).intValue());
                    pipeline.addProperty("queued",((Number)args[1]).intValue());
                    pipeline.addProperty("jobPending",((Number)args[2]).intValue());
                    pipeline.addProperty("actualNativePending",((Number)args[3]).intValue());
                    report.add("lastPipeline",pipeline);
                }
                Component live=null;
                for(var message:messages)if(key(message).equals("progress_stream"))live=message;
                if(live!=null) {
                    var args=((TranslatableContents)live.getContents()).getArgs();
                    var progress=new JsonObject();progress.addProperty("phase",((Component)args[0]).getString());
                    progress.addProperty("checked",((Number)args[1]).intValue());
                    progress.addProperty("scheduled",((Number)args[2]).intValue());
                    int found=((Number)args[3]).intValue();progress.addProperty("found",found);
                    report.add("lastProgress",progress);
                    if(found>0) {
                        command(server,"page");
                        if(has("result") && args[0] instanceof Component state
                                && state.getContents() instanceof TranslatableContents text
                                && text.getKey().endsWith("phase_searching"))resultWhilePlanning=true;
                    }
                }
                writeSnapshot();
            }
            if(focusPending && has("result")) { focusOnNativeResult(server,now);return; }
            long queries=System.getProperty("neighbors.probe.searchFixture", "").equals("selectors")?7:2;
            long fixtureSeconds=60+queries*(design.inherent.skyweavecompat.terrain.structure.StructureLocator.readPolicy(source).value().timeLimitSeconds()+30L);
            if (now - started > java.util.concurrent.TimeUnit.SECONDS.toNanos(fixtureSeconds))
                throw new AssertionError("Search fixture exceeded its policy-derived bounded duration");
            if (has("failed")) throw new AssertionError("Search failed: " + messages);
            if(phase==2) {
                command(server,"status");
                for(var message:messages)if(key(message).equals("progress_stream")) {
                    var args=((TranslatableContents)message.getContents()).getArgs();
                    if(args[0] instanceof Component state && state.getContents() instanceof TranslatableContents phaseText
                            && phaseText.getKey().endsWith("phase_searching") && ((Number)args[1]).intValue()>0)
                        streamedVerification=true;
                }
                for(var message:messages)if(key(message).equals("pipeline")) {
                    var args=((TranslatableContents)message.getContents()).getArgs();
                    if(((Number)args[1]).intValue()>128 || ((Number)args[3]).intValue()>2)
                        throw new AssertionError("Discovery exceeded queue/native admission bounds");
                }
            }
            if (firstResult < 0 && has("result")) firstResult = now - runStarted;
            if (phase <= 1 && (has("complete") || has("partial"))) {
                var terminal = messages.stream().filter(m -> Set.of("complete", "partial").contains(key(m))).findFirst().orElseThrow();
                var values = ((TranslatableContents)terminal.getContents()).getArgs();
                var run = new JsonObject(); run.addProperty("outcome", key(terminal));
                run.addProperty("query",query);
                run.addProperty("firstResultMillis", firstResult < 0 ? -1 : firstResult / 1_000_000.0);
                run.addProperty("resultBeforeEnumerationCompleted",resultWhilePlanning);
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
                if(System.getProperty("neighbors.probe.searchFixture", "").equals("time")) {
                    if(!key(terminal).equals("partial") || !has("limit_time"))
                        throw new AssertionError("Time exhaustion did not preserve explicit partial coverage");
                    run.addProperty("timeLimitVerified",true);
                }
                command(server, "page");
                if (((Number)values[1]).intValue() > 0 && !has("page"))
                    throw new AssertionError("Page shortcut did not show retained results");
                if(selector==0)report.add(phase == 0 ? "cold" : "warm", run);
                else {
                    var name=selector==1?"explicitId":"managedTag";
                    if(!selectorRuns.has(name))selectorRuns.add(name,new JsonObject());
                    var pair=selectorRuns.getAsJsonObject(name);
                    pair.addProperty("query",query);pair.add(phase==0?"cold":"warm",run);
                    report.add("selectorControls",selectorRuns);
                }
                if(selector==0 && selectedId==null)for(var message:messages)if(key(message).equals("result")) {
                    selectedId=((TranslatableContents)message.getContents()).getArgs()[0].toString();break;
                }
                if(phase==1 && selector<2 && System.getProperty("neighbors.probe.searchFixture", "").equals("selectors")) {
                    if(selectedId==null)throw new AssertionError("Selector controls require a real native family result");
                    selector++;
                    query="search "+queryRadius+" structure="
                            +(selector==1?selectedId:"#skyweave_compat:managed/"+familyQuery.substring("family=".length()));
                    phase=0;messages.clear();phaseTick=ticks;runStarted=System.nanoTime();firstResult=-1;resultWhilePlanning=false;
                    command(server,query);
                    if(!has("started"))throw new AssertionError("Selector query was not admitted: "+query);
                    return;
                }
                if(phase==1 && selector==2 && System.getProperty("neighbors.probe.searchFixture", "").equals("selectors")) {
                    savedStartControl();assertNoSearchTickets(server);
                    report.addProperty("leasesReleased",true);report.addProperty("permissionDenied",true);
                    report.addProperty("complete",true);finish(server,null);return;
                }
                if (phase == 1 && Set.of("cap","time").contains(System.getProperty("neighbors.probe.searchFixture", ""))) {
                    assertNoSearchTickets(server); report.addProperty("leasesReleased", true);
                    report.addProperty("complete", true); finish(server, null); return;
                }
                if (terminal.getString().contains("skyweave_compat.structures.")) throw new AssertionError("Missing readable fallback");
                if (maxGap > java.util.concurrent.TimeUnit.SECONDS.toNanos(5)) throw new AssertionError("Search stalled ordinary server ticks");
                phase++; messages.clear(); phaseTick = ticks;
                if (phase == 1) {
                    runStarted = System.nanoTime(); firstResult = -1; resultWhilePlanning=false;command(server, query);
                }
                else command(server, "search 131072 family=villages");
            } else if (phase == 2 && ticks - phaseTick >= 40 && (streamedVerification || ticks-phaseTick>=200)) {
                if(!streamedVerification)throw new AssertionError("Large search did not verify candidates before enumeration ended");
                report.addProperty("nativeVerificationBeforeEnumerationCompleted",true);
                command(server, "cancel");
                if (!has("cancelled")) throw new AssertionError("Cancellation missing");
                assertNoSearchTickets(server);
                for (int retry = 0; retry < 3; retry++) {
                    command(server, "search 131072 family=villages");
                    command(server, "cancel");
                    assertNoSearchTickets(server);
                }
                messages.clear(); command(server, "search 1 family=villages"); phase = 3; phaseTick = ticks;
            } else if (phase == 3 && (has("complete") || has("partial")) && ticks - phaseTick >= 20) {
                var completion = messages.stream().filter(m -> Set.of("complete","partial").contains(key(m))).findFirst().orElseThrow();
                if (((Number)((TranslatableContents)completion.getContents()).getArgs()[1]).intValue() == 0
                        && (!has(key(completion).equals("complete") ? "empty_complete" : "empty_partial") || has("page")))
                    throw new AssertionError("Empty search displayed a blank page or concealed incomplete coverage");
                long completions = messages.stream().filter(m -> Set.of("complete","partial").contains(key(m))).count();
                if (completions != 1) throw new AssertionError("Cancelled callback published stale completion");
                report.addProperty("emptySearchOutcome",key(completion));
                messages.clear(); command(server, "search 100 structure=minecraft:stronghold");
                if (!has("invalid")) throw new AssertionError("Unsupported concentric placement was not qualified");
                messages.clear(); command(server, "search 100 structure=minecraft:not_registered");
                if (!has("invalid")) throw new AssertionError("Unknown structure accepted");
                report.addProperty("cancelledCallbackIsolated", true); report.addProperty("permissionDenied", true);
                assertNoSearchTickets(server); report.addProperty("leasesReleased", true);
                savedStartControl();
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
    private static void savedStartControl() {
        if(Boolean.getBoolean("neighbors.probe.savedStartsOnly")) {
            if(!Boolean.getBoolean("neighbors.probe.savedStartsExercised"))throw new AssertionError("Saved-start discriminator was not exercised");
            report.addProperty("forcedNewStartRejection",true);
        }
    }
    private static void focusOnNativeResult(MinecraftServer server,long now) throws Exception {
        var message=messages.stream().filter(value->key(value).equals("result")).findFirst().orElseThrow();
        var args=((TranslatableContents)message.getContents()).getArgs();
        var click=((Component)args[1]).getStyle().getClickEvent();
        if(click==null || click.getAction()!=ClickEvent.Action.SUGGEST_COMMAND)throw new AssertionError("Native result has no coordinate action");
        var parts=click.getValue().split(" ");
        if(parts.length!=5 || !parts[0].equals("/tp") || !parts[1].equals("@s"))throw new AssertionError("Unexpected native coordinate action");
        int x=Integer.parseInt(parts[2]),z=Integer.parseInt(parts[4]);
        var broad=new JsonObject();broad.addProperty("query",query);broad.addProperty("firstId",args[0].toString());
        broad.addProperty("firstX",x);broad.addProperty("firstZ",z);
        broad.addProperty("firstResultMillis",(now-runStarted)/1e6);
        var job=acceptedJob(server);
        boolean enumerationDone=(Boolean)field(job,"enumerationDone");
        broad.addProperty("resultBeforeEnumerationCompleted",!enumerationDone);
        broad.addProperty("queuedAtFirstResult",((java.util.Collection<?>)field(job,"incoming")).size()
                +((java.util.Collection<?>)field(job,"ready")).size());
        if(enumerationDone && !Boolean.getBoolean("neighbors.probe.savedStartsOnly"))
            throw new AssertionError("Fresh broad fixture did not exercise a native result during enumeration");
        command(server,"cancel");command(server,"page");
        if(!has("cancelled") || pageResults().isEmpty())throw new AssertionError("Broad native result was lost on explicit cancellation");
        broad.addProperty("resultRetainedAfterCancel",true);report.add("broadNativeDiscovery",broad);
        // Reopen repeats the same bounded domain, even if another saved start finishes first.
        x=Integer.getInteger("neighbors.probe.selectorFocusX",x);
        z=Integer.getInteger("neighbors.probe.selectorFocusZ",z);
        if(Math.abs((long)x)>29999983 || Math.abs((long)z)>29999983)throw new AssertionError("Invalid saved selector focus");
        if(Boolean.getBoolean("neighbors.probe.savedStartsOnly")) {
            var family=java.util.Arrays.stream(design.inherent.skyweavecompat.terrain.structure.StructureFamily.values())
                    .filter(value->familyQuery.equals("family="+value.key)).findFirst().orElseThrow();
            var distribution=((design.inherent.skyweavecompat.terrain.structure.StructurePlacementState)
                    server.overworld().getChunkSource().getGeneratorState()).skyweave$structureDistribution();
            if(distribution.possibleNewStart(family,Math.floorDiv(x,16),Math.floorDiv(z,16)))
                throw new AssertionError("Negative new-start predicate is inactive");
            savedStartControl();report.addProperty("negativeNewStartProbeAtKnownMarker",true);
        }
        var focus=new JsonObject();focus.addProperty("x",x);focus.addProperty("z",z);focus.addProperty("radius",128);
        report.add("selectorFocus",focus);
        source=source.withPosition(new net.minecraft.world.phys.Vec3(x,0,z));
        focusPending=false;queryRadius=128;query="search "+queryRadius+" "+familyQuery;
        phase=0;phaseTick=ticks;messages.clear();runStarted=now;firstResult=-1;resultWhilePlanning=false;
        command(server,query);writeSnapshot();
    }
    private static void finish(MinecraftServer server, Throwable failure) {
        active = false;
        report.addProperty("fixturePhase",failure==null?"finished":"failed");
        report.addProperty("elapsedSeconds", (System.nanoTime() - started) / 1e9);
        report.addProperty("maximumTickGapMillis", maxGap / 1e6);
        if (failure != null) {
            report.addProperty("complete", false); report.addProperty("error", failure.toString());
            report.addProperty("failedPhase",phase);
            report.add("messageKinds",new com.google.gson.Gson().toJsonTree(messages.stream().map(StructureLocatorProbe::key).toList()));
            failure.printStackTrace();
        }
        try { Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n"); }
        catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
        server.halt(false);
    }
    static void assertNoSearchTickets(MinecraftServer server) throws Exception {
        assertNoTickets(server, Set.of("skyweave_structure_search"));
    }
    static void assertNoTickets(MinecraftServer server, Set<String> types) throws Exception {
        ProbeTickets.assertAbsent(server,types);
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
