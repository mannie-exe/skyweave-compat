package design.inherent.skyweavecompat.probe;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import design.inherent.reform.definition.DocumentId;
import design.inherent.reform.neoforge.configuration.ServerSettings;
import design.inherent.reform.owner.CommitReceipt;
import design.inherent.skyweavecompat.configuration.ConfigLocations;
import design.inherent.skyweavecompat.terrain.structure.SearchSettings;
import design.inherent.skyweavecompat.terrain.structure.StructureLocator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import design.inherent.reform.operation.SearchReport;
import design.inherent.reform.operation.WorkBudget;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.loading.FMLPaths;

/** Actual locator admission/leases under live policy changes; the native worker is held before registration. */
final class LocatorPolicyProbe {
    private static final JsonObject report = new JsonObject();
    private static final List<Component> messages = new ArrayList<>();
    private static final CountDownLatch gate = new CountDownLatch(1);
    private static final AtomicBoolean entered = new AtomicBoolean();
    private static CommandSourceStack source;
    private static net.minecraft.server.level.ServerPlayer player;
    private static Object state, job;
    private static Path output;
    private static boolean active;
    private static int stage, ticks, changed;
    private static long began, deadline, taskCount;
    private static SearchSettings captured;
    private static CompletableFuture<? extends CommitReceipt<?>> applying;
    private static Path settingsFile;
    private static String savedPolicy;

    static void start(MinecraftServer server, Path destination) throws Exception {
        output=destination; began=System.nanoTime(); active=true;
        var spawn = server.overworld().getSharedSpawnPos();
        player = ProbePlayers.stationary(server, "LocatorPolicy", spawn.above(2));
        server.getPlayerList().op(player.getGameProfile());
        source = player.createCommandSourceStack().withPosition(net.minecraft.world.phys.Vec3.atCenterOf(spawn));
        var states=StructureLocator.class.getDeclaredField("servers"); states.setAccessible(true);
        state=((Map<?,?>)states.get(null)).get(server);
        check(state!=null,"No actual locator context");
        captured=StructureLocator.readPolicy(source).value();
        check(captured.equals(new SearchSettings(16,5,2,1)), "Authored startup policy was not selected");
        // Do not block the server thread or alter the product's chunk API. Holding this existing
        // admission worker lets actual tickets/futures remain observable for several server ticks.
        worker().execute(() -> {
            entered.set(true);
            try { if (!gate.await(90,TimeUnit.SECONDS)) throw new IllegalStateException("Native admission fixture gate expired"); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        });
        report.addProperty("fixture","actual Sky Archipelago structure-search policy publication");
        report.addProperty("complete",false); save();
    }
    static void tick(MinecraftServer server) {
        if (!active) return;
        try {
            ticks++;
            ProbePlayers.drainChat(player, messages);
            check(System.nanoTime()-began<TimeUnit.SECONDS.toNanos(90),"Policy fixture exceeded90seconds");
            if (stage==0 && entered.get()) {
                long before=System.nanoTime(); command("search 12800 family=portals"); long after=System.nanoTime();
                check(has("started"),"Actual locator query was not admitted");
                job=field(state,"job"); deadline=(long)field(job,"deadline");
                check(field(job,"limits")==captured,"New request did not capture its actual policy object");
                check(deadline>=before+TimeUnit.SECONDS.toNanos(5) && deadline<=after+TimeUnit.SECONDS.toNanos(5),"Deadline is not captured from admission");
                stage=1;
            } else if (stage==1 && nativePending()==2) {
                check(((Set<?>)field(job,"leases")).size()==2 && ((WorkBudget.Group)field(job,"work")).pending()==2,
                        "Held admission lacks two actual ticket/future reservations");
                var document = ServerSettings.admittedDocument(server, DocumentId.parse("skyweave_compat:search")).orElseThrow();
                settingsFile = ConfigLocations.file(FMLPaths.CONFIGDIR.get(), SearchSettings.FILE);
                savedPolicy = new GsonBuilder().setPrettyPrinting().create().toJson(new SearchSettings(32,600,1,2)) + "\n";
                Files.writeString(settingsFile, savedPolicy);
                applying = document.reloadFromSaved(source, 2).toCompletableFuture();
                stage=5;
                report.addProperty("actualPendingBeforeReduction",2);
            } else if (stage==5 && applying.isDone()) {
                var receipt = applying.join();
                check(receipt.persistence()==CommitReceipt.Persistence.NOT_ATTEMPTED && receipt.application()==CommitReceipt.Application.APPLIED,
                        "Shared policy receipt did not confirm apply-only: "+receipt);
                check(Files.readString(settingsFile).equals(savedPolicy),"Apply-only rewrote externally saved bytes");
                check(StructureLocator.readPolicy(source).value().equals(new SearchSettings(32,600,1,2)),
                        "Shared apply did not update actual locator policy");
                check(field(job,"limits")==captured && (long)field(job,"deadline")==deadline,"Publication rewrote admitted limits/deadline");
                taskCount=worker().getTaskCount(); changed=ticks; stage=2;
                report.addProperty("sharedApplyOnlyUpdatesRealPolicy",true);
                report.addProperty("sharedSavedBytesUnchanged",true);
            } else if (stage==2 && ticks-changed>=3) {
                check(nativePending()==2 && worker().getTaskCount()==taskCount,"Lowered cap admitted new work or erased old accounting");
                check(((Set<?>)field(job,"leases")).size()==2 && (long)field(job,"deadline")==deadline,"Held work lost leases or extended its deadline");
                report.addProperty("lowerCapPreservesPendingAndBlocksAdmission",true);
                gate.countDown(); stage=3;
            } else if (stage==3 && !(boolean)field(job,"running") && nativePending()==0 && (int)field(state,"metadataPending")==0) {
                check(!has("failed"),"Native search failed: "+messages);
                check((long)field(job,"deadline")==deadline && field(job,"limits")==captured,"Old request adopted the later600second policy");
                ProbeTickets.assertAbsent(server,Set.of("skyweave_structure_search"));
                report.addProperty("nativeCallbacksDrainAfterRelease",true);
                report.addProperty("capturedDeadlineAndLimitsPreserved",true);
                report.addProperty("firstOutcome",((SearchReport<?>)field(job,"report")).status().outcome().name());
                report.addProperty("firstRequestExpiredAtOriginalDeadline",has("limit_time") && System.nanoTime()>=deadline);
                messages.clear(); long before=System.nanoTime(); command("search 1 family=portals"); long after=System.nanoTime();
                check(has("started"),"Next request did not admit after drainage");
                var next=field(state,"job"); var current=StructureLocator.readPolicy(source).value();
                check(field(next,"limits")==current && current.timeLimitSeconds()==600 && current.concurrentChunks()==1,"New request did not use published limits");
                long nextDeadline=(long)field(next,"deadline");
                check(nextDeadline>=before+TimeUnit.SECONDS.toNanos(600) && nextDeadline<=after+TimeUnit.SECONDS.toNanos(600),"Next deadline did not use new policy");
                command("cancel"); stage=4;
            } else if (stage==4 && nativePending()==0 && (int)field(state,"metadataPending")==0) {
                ProbeTickets.assertAbsent(server,Set.of("skyweave_structure_search"));
                report.addProperty("nextRequestUsesNewPolicyAndCancels",true);
                report.addProperty("ticks",ticks); report.addProperty("complete",true);
                report.addProperty("limits","Real native requests and tickets; admission worker intentionally held, not a timing/performance measurement or UI/remote transport claim");
                active=false; save(); server.halt(false);
            }
        } catch (Throwable failure) {
            gate.countDown(); active=false; report.addProperty("complete",false); report.addProperty("error",failure.toString());
            failure.printStackTrace(); save(); server.halt(false);
        }
    }
    private static ThreadPoolExecutor worker() throws Exception { return (ThreadPoolExecutor)field(state,"requests"); }
    private static int nativePending() throws Exception { return ((WorkBudget)field(state,"work")).inUse(); }
    private static Object field(Object owner, String name) throws Exception {
        var field=owner.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(owner);
    }
    private static void command(String tail) throws Exception {
        source.getServer().getCommands().getDispatcher().execute("skyweavecompat structures "+tail,source);
        ProbePlayers.drainChat(player, messages);
    }
    private static boolean has(String key) {
        return messages.stream().anyMatch(message -> message.getContents() instanceof TranslatableContents text
                && text.getKey().equals("skyweave_compat.structures."+key));
    }
    private static void check(boolean value, String reason) { if (!value) throw new AssertionError(reason); }
    private static void save() {
        report.addProperty("elapsedSeconds",(System.nanoTime()-began)/1e9);
        try { Files.writeString(output,new GsonBuilder().setPrettyPrinting().create().toJson(report)+"\n"); }
        catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }
}
