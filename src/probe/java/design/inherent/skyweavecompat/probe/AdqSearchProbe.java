/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.probe;

import com.google.gson.*;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Supplier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Native route and request observations in a marked disposable world. Never part of the product. */
public final class AdqSearchProbe {
    public static final ThreadLocal<Boolean> LOCATE = new ThreadLocal<>();
    public static final ThreadLocal<JsonObject> REQUEST = new ThreadLocal<>();
    private static final JsonObject REPORT = new JsonObject();
    private static final JsonArray REQUESTS = new JsonArray();
    private static volatile MinecraftServer server;
    private static volatile boolean finished;
    private static long requestId;
    private int ticks, postFinish;
    private int initialQuestCount;
    private final boolean route=Boolean.getBoolean("neighbors.probe.adqRoute");
    private long previousTick,maxInterTickNanos;
    public static MinecraftServer server(){return server;}
    public AdqSearchProbe() {
        REPORT.add("requests",REQUESTS);
        REPORT.addProperty("scope","Actual ADQ locate/chunk/future correlation; optional first-read abort is observer-only, not route success or performance acceptance");
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event)->server=event.getServer());
        NeoForge.EVENT_BUS.addListener(this::tick);
    }
    private void tick(ServerTickEvent.Post event) {
        if(event.getServer()!=server)return;
        ticks++;
        long now=System.nanoTime();if(previousTick!=0)maxInterTickNanos=Math.max(maxInterTickNanos,now-previousTick);previousTick=now;
        if(ticks==2) {
            if(route) {
                try{var type=Class.forName("com.ladderstar.adq.QuestGenerator");
                    initialQuestCount=((List<?>)type.getMethod("getAvailableQuests").invoke(null)).size();
                    REPORT.addProperty("initialQuestCount",initialQuestCount);inspectPersistence("initial");
                    type.getMethod("generateNewQuestAsync",ServerLevel.class).invoke(null,server.overworld());
                    synchronized(REPORT){REPORT.addProperty("publicEntryReturned",true);}}
                catch(Throwable failure){synchronized(REPORT){REPORT.addProperty("invocationError",failure.toString());}finished=true;}
            } else {
            CompletableFuture.runAsync(()->{
                try {
                    var type=Class.forName("com.ladderstar.adq.QuestGenerator");
                    var method=java.util.Arrays.stream(type.getDeclaredMethods()).filter(m->m.getName().equals("lambda$generateNewQuestAsync$9")).findFirst().orElseThrow();
                    method.setAccessible(true);method.invoke(null,server.overworld(),null,false,null,List.of());
                }catch(Throwable failure){synchronized(REPORT){REPORT.addProperty("invocationError",failure.toString());}}
                finally{finished=true;}
            });
            }
        }
        synchronized(REPORT){REPORT.addProperty("serverTicks",ticks);REPORT.addProperty("maxInterTickNanosObservationOnly",maxInterTickNanos);}
        if(route && ticks>=2) {
            try {
                AdqLifetimeProbe.tick(server);
                if(AdqLifetimeProbe.enabled())REPORT.add("lifecycleControl",AdqLifetimeProbe.report());
                var type=Class.forName("com.ladderstar.adq.QuestGenerator");var field=type.getDeclaredField("availableQuests");field.setAccessible(true);
                var quests=(List<?>)field.get(null);boolean generating=(Boolean)type.getMethod("isGenerating").invoke(null);
                synchronized(REPORT){REPORT.addProperty("nativeQuestCount",quests.size());REPORT.addProperty("nativeGenerating",generating);}
                if(AdqTicketFaultProbe.fired() && !generating) {
                    synchronized(REPORT){inspectOwned();var owned=REPORT.getAsJsonObject("ownedState");
                        if(owned!=null && owned.get("ownedJobs").getAsInt()==0 && owned.get("nativeReservations").getAsInt()==0 && owned.get("activeRootTasks").getAsInt()==0 && owned.get("endpointLeases").getAsInt()==0)finished=true;
                    }
                }
                if(quests.size()>initialQuestCount && !generating) {
                    synchronized(REPORT){var rows=new JsonArray();for(Object quest:quests) {
                        var row=new JsonObject();for(String name:new String[]{"getQuestId","getStartingPos","getEndingPos","getSchematicName","getWeightClass","getActualWeight","getDistance","getCargoEntityId"})
                            row.addProperty(name,String.valueOf(quest.getClass().getMethod(name).invoke(quest)));rows.add(row);
                    }REPORT.add("nativeQuests",rows);REPORT.addProperty("routeRegistered",true);inspectPersistence("final");}
                    finished=true;
                }
                if(requestId>0 && !generating && !finished && !AdqLifetimeProbe.enabled()) {
                    synchronized(REPORT){inspectOwned();var owned=REPORT.getAsJsonObject("ownedState");
                        if(owned!=null && owned.get("ownedJobs").getAsInt()==0 && owned.get("activeRootTasks").getAsInt()==0 && owned.get("queuedRootTasks").getAsInt()==0 && owned.get("nativeReservations").getAsInt()==0) {
                            REPORT.addProperty("nativeCompletedWithoutNewRoute",true);finished=true;
                        }
                    }
                }
            }catch(Throwable failure){synchronized(REPORT){REPORT.addProperty("routeObservationError",failure.toString());}finished=true;}
        }
        if(finished && ++postFinish>=5 || ticks>=(route?3600:1200)) {
            synchronized(REPORT){REPORT.addProperty("nativeInvocationReturned",route?REPORT.has("publicEntryReturned"):finished);REPORT.addProperty("observed",REQUESTS.size()>0);
                REPORT.addProperty("terminal",true);REPORT.addProperty("observedRequests",requestId);inspectOwned();
                if(AdqTicketFaultProbe.fired()){REPORT.add("faultControl",AdqTicketFaultProbe.observe());AdqTicketFaultProbe.releaseForeign();}save();}
            server.halt(false);
        }
    }
    public static <T> T locate(Supplier<T> call) {
        var previous=LOCATE.get();LOCATE.set(true);
        try{return call.get();}finally{if(previous==null)LOCATE.remove();else LOCATE.set(previous);}
    }
    private static void inspectPersistence(String phase) throws Exception {
        var field=Class.forName("com.ladderstar.adq.QuestGenerator").getDeclaredField("questFilePath");field.setAccessible(true);
        var path=(Path)field.get(null);var row=new JsonObject();row.addProperty("exists",path!=null && Files.isRegularFile(path));
        if(path!=null && Files.isRegularFile(path)) {
            byte[] bytes=Files.readAllBytes(path);row.addProperty("sha256",java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)));
            var data=JsonParser.parseString(new String(bytes,java.nio.charset.StandardCharsets.UTF_8));
            row.add("stored",data);row.addProperty("storedQuestCount",data.getAsJsonArray().size());
        }
        REPORT.add(phase+"Persistence",row);
    }
    public static JsonObject begin(int x,int z,String status,boolean create) {
        if(!Boolean.TRUE.equals(LOCATE.get()))return null;
        return beginActual(x,z,status,create,"ServerChunkCache.getChunk");
    }
    public static JsonObject beginBridge(int x,int z,String status,boolean create) {
        return beginActual(x,z,status,create,"DeliverySearch.chunk");
    }
    private static JsonObject beginActual(int x,int z,String status,boolean create,String ingress) {
        synchronized(REPORT) {
            var row=new JsonObject();row.addProperty("id",++requestId);row.addProperty("x",x);row.addProperty("z",z);row.addProperty("status",status);row.addProperty("create",create);
            row.addProperty("ingress",ingress);
            row.addProperty("ingressThread",Thread.currentThread().getName());row.addProperty("ingressNanos",System.nanoTime());
            if(REQUESTS.size()<128)REQUESTS.add(row);return row;
        }
    }
    public static <T> Supplier<T> carry(Supplier<T> supplied,JsonObject request) {
        return ()->{var prior=REQUEST.get();REQUEST.set(request);try{return supplied.get();}finally{if(prior==null)REQUEST.remove();else REQUEST.set(prior);}};
    }
    public static void future(String name,CompletableFuture<?> future) {
        var row=REQUEST.get();if(row==null)return;
        synchronized(REPORT) {
            row.addProperty(name+"Identity",System.identityHashCode(future));row.addProperty(name+"Thread",Thread.currentThread().getName());row.addProperty(name+"InitiallyDone",future.isDone());
            row.add(name+"ObservedServerTicksAtRegistration",REPORT.get("serverTicks"));
        }
        future.whenComplete((value,failure)->{synchronized(REPORT){row.addProperty(name+"CompletionNanos",System.nanoTime());row.addProperty(name+"Failed",failure!=null);row.add(name+"ObservedServerTicksAtCompletion",REPORT.get("serverTicks"));}});
    }
    public static void waitBoundary(boolean returned) {
        var row=REQUEST.get();if(row==null)return;
        synchronized(REPORT) {
            String name=returned?"serverManagedWaitReturn":"serverManagedWaitEntry";
            row.addProperty(name+"Nanos",System.nanoTime());row.addProperty(name+"Tick",server.getTickCount());
            row.addProperty(name+"Thread",Thread.currentThread().getName());
        }
    }
    public static void end(JsonObject row) {
        if(row==null)return;
        synchronized(REPORT){row.addProperty("returnedNanos",System.nanoTime());row.add("observedServerTicksAtReturn",REPORT.get("serverTicks"));if(!Boolean.getBoolean("neighbors.probe.adqRoute") || requestId%16==0)save();}
        if(Boolean.getBoolean("neighbors.probe.adqFirstRead"))throw new IllegalStateException("Disposable observer stopped after one actual ADQ chunk read");
    }
    private static void inspectOwned() {
        if(!Boolean.getBoolean("neighbors.probe.adqBridge"))return;
        try {
            var type=Class.forName("design.inherent.skyweavecompat.placement.DeliverySearch");var field=type.getDeclaredField("STATES");field.setAccessible(true);
            var state=((java.util.Map<?,?>)field.get(null)).get(server);var row=new JsonObject();
            field=state.getClass().getDeclaredField("active");field.setAccessible(true);row.addProperty("ownedJobs",field.get(state)==null?0:1);
            field=state.getClass().getDeclaredField("nativeRequests");field.setAccessible(true);row.addProperty("nativeReservations",((java.util.concurrent.atomic.AtomicInteger)field.get(state)).get());
            try{field=state.getClass().getDeclaredField("worker");field.setAccessible(true);var worker=(ThreadPoolExecutor)field.get(state);row.addProperty("activeRootTasks",worker.getActiveCount());row.addProperty("queuedRootTasks",worker.getQueue().size());}
            catch(NoSuchFieldException historical){row.addProperty("workerScope","Historical dev1 used native common pool");}
            type=Class.forName("design.inherent.skyweavecompat.placement.DeliveryChunks");field=type.getDeclaredField("ACTIVE");field.setAccessible(true);
            row.addProperty("endpointLeases",((java.util.Map<?,?>)field.get(null)).size());REPORT.add("ownedState",row);
        }catch(Throwable failure){REPORT.addProperty("ownedStateError",failure.toString());}
    }
    private static void save() {
        try{Files.writeString(Path.of(System.getProperty("neighbors.probe.output")),new GsonBuilder().setPrettyPrinting().create().toJson(REPORT));}
        catch(java.io.IOException failure){throw new java.io.UncheckedIOException(failure);}
    }
}
