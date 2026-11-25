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

/** Read-only request/future correlation in a marked disposable world. Never part of the product. */
public final class AdqSearchProbe {
    public static final ThreadLocal<Boolean> LOCATE = new ThreadLocal<>();
    public static final ThreadLocal<JsonObject> REQUEST = new ThreadLocal<>();
    private static final JsonObject REPORT = new JsonObject();
    private static final JsonArray REQUESTS = new JsonArray();
    private static volatile MinecraftServer server;
    private static volatile boolean finished;
    private static long requestId;
    private int ticks, postFinish;
    public AdqSearchProbe() {
        REPORT.add("requests",REQUESTS);
        REPORT.addProperty("scope","Actual ADQ locate/chunk/future correlation; optional first-read abort is observer-only, not route success or performance acceptance");
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event)->server=event.getServer());
        NeoForge.EVENT_BUS.addListener(this::tick);
    }
    private void tick(ServerTickEvent.Post event) {
        if(event.getServer()!=server)return;
        ticks++;
        if(ticks==2) {
            CompletableFuture.runAsync(()->{
                try {
                    var type=Class.forName("com.ladderstar.adq.QuestGenerator");
                    var method=java.util.Arrays.stream(type.getDeclaredMethods()).filter(m->m.getName().equals("lambda$generateNewQuestAsync$9")).findFirst().orElseThrow();
                    method.setAccessible(true);method.invoke(null,server.overworld(),null,false,null,List.of());
                }catch(Throwable failure){synchronized(REPORT){REPORT.addProperty("invocationError",failure.toString());}}
                finally{finished=true;}
            });
        }
        synchronized(REPORT){REPORT.addProperty("serverTicks",ticks);}
        if(finished && ++postFinish>=5 || ticks>=1200) {
            synchronized(REPORT){REPORT.addProperty("nativeInvocationReturned",finished);REPORT.addProperty("observed",REQUESTS.size()>0);save();}
            server.halt(false);
        }
    }
    public static <T> T locate(Supplier<T> call) {
        var previous=LOCATE.get();LOCATE.set(true);
        try{return call.get();}finally{if(previous==null)LOCATE.remove();else LOCATE.set(previous);}
    }
    public static JsonObject begin(int x,int z,String status,boolean create) {
        if(!Boolean.TRUE.equals(LOCATE.get()))return null;
        synchronized(REPORT) {
            if(REQUESTS.size()>=128)throw new IllegalStateException("Disposable ADQ request observation bound reached");
            var row=new JsonObject();row.addProperty("id",++requestId);row.addProperty("x",x);row.addProperty("z",z);row.addProperty("status",status);row.addProperty("create",create);
            row.addProperty("ingressThread",Thread.currentThread().getName());row.addProperty("ingressNanos",System.nanoTime());REQUESTS.add(row);return row;
        }
    }
    public static <T> Supplier<T> carry(Supplier<T> supplied,JsonObject request) {
        return ()->{var prior=REQUEST.get();REQUEST.set(request);try{return supplied.get();}finally{if(prior==null)REQUEST.remove();else REQUEST.set(prior);}};
    }
    public static void future(String name,CompletableFuture<?> future) {
        var row=REQUEST.get();if(row==null)return;
        synchronized(REPORT) {
            row.addProperty(name+"Identity",System.identityHashCode(future));row.addProperty(name+"Thread",Thread.currentThread().getName());row.addProperty(name+"InitiallyDone",future.isDone());
        }
        future.whenComplete((value,failure)->{synchronized(REPORT){row.addProperty(name+"CompletionNanos",System.nanoTime());row.addProperty(name+"Failed",failure!=null);}});
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
        synchronized(REPORT){row.addProperty("returnedNanos",System.nanoTime());save();}
        if(Boolean.getBoolean("neighbors.probe.adqFirstRead"))throw new IllegalStateException("Disposable observer stopped after one actual ADQ chunk read");
    }
    private static void save() {
        try{Files.writeString(Path.of(System.getProperty("neighbors.probe.output")),new GsonBuilder().setPrettyPrinting().create().toJson(REPORT));}
        catch(java.io.IOException failure){throw new java.io.UncheckedIOException(failure);}
    }
}
