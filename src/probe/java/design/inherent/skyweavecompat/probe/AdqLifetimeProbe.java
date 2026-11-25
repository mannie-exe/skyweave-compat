/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.probe;

import com.google.gson.JsonObject;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** Holds only a fixture proxy; never cancels or completes the actual native chunk future. */
public final class AdqLifetimeProbe {
    private static final JsonObject REPORT=new JsonObject();
    private static CompletableFuture<?> original,held;
    private static Object expiredJob;
    private static int phase,ticks;
    private AdqLifetimeProbe(){}
    public static boolean enabled(){return Boolean.getBoolean("neighbors.probe.adqLifetime");}
    public static synchronized <T> CompletableFuture<T> gate(CompletableFuture<T> nativeFuture) {
        if(!enabled() || held!=null || AdqSearchProbe.REQUEST.get()==null)return nativeFuture;
        original=nativeFuture;var proxy=new CompletableFuture<T>();held=proxy;
        REPORT.addProperty("fixtureProxyOnly",true);REPORT.addProperty("nativeInitiallyDone",nativeFuture.isDone());return proxy;
    }
    private static Object field(Object owner,String name) throws ReflectiveOperationException {
        var field=owner.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(owner);
    }
    private static Object state(MinecraftServer server) throws ReflectiveOperationException {
        var field=Class.forName("design.inherent.skyweavecompat.placement.DeliverySearch").getDeclaredField("STATES");field.setAccessible(true);
        return ((Map<?,?>)field.get(null)).get(server);
    }
    public static void tick(MinecraftServer server) throws Exception {
        if(!enabled() || held==null || phase==4)return;
        Object state=state(server);Object active=field(state,"active");
        int reservations=((AtomicInteger)field(state,"nativeRequests")).get();
        var worker=(ThreadPoolExecutor)field(state,"worker");
        var type=Class.forName("com.ladderstar.adq.QuestGenerator");
        boolean generating=(boolean)type.getMethod("isGenerating").invoke(null);
        if(phase==0) {
            if(active==null)throw new AssertionError("Lifecycle control lacks an actual active native search");
            if(Boolean.getBoolean("neighbors.probe.adqLogout")) {
                AdqSearchProbe.logoutLifecycleActor();
                REPORT.addProperty("actualRequestingPlayerLogout",true);
            }else if(Boolean.getBoolean("neighbors.probe.adqExpiry")) {
                // Change only fixture time input; production Pre tick must perform cancellation.
                var deadline=active.getClass().getDeclaredField("deadline");deadline.setAccessible(true);
                deadline.setLong(active,System.nanoTime()-1);expiredJob=active;
                REPORT.addProperty("actualJobDeadlineMovedIntoPast",true);
            }else {
                var method=active.getClass().getDeclaredMethod("cancel",String.class);method.setAccessible(true);method.invoke(active,"Disposable native lifecycle cancellation control");
            }
            REPORT.addProperty("reservationsAtCancel",reservations);phase=1;
        }else if(phase==1 && active==null && !generating && worker.getActiveCount()==0) {
            if(reservations!=1 || held.isDone() || original.isCancelled())throw new AssertionError("Retired native request lost custody");
            if(expiredJob!=null) {
                if(!String.valueOf(field(expiredJob,"outcome")).contains("expired"))throw new AssertionError("Native tick did not expire the actual job");
                REPORT.addProperty("actualTickExpiryObserved",true);expiredJob=null;
            }
            type.getMethod("generateNewQuestAsync",ServerLevel.class).invoke(null,server.overworld());
            REPORT.addProperty("newAttemptWhileHeld",true);phase=2;ticks=0;
        }else if(phase==2 && ++ticks>=5 && !generating && worker.getActiveCount()==0 && original.isDone()) {
            if(field(state,"active")!=null || reservations!=1 || held.isDone() || original.isCancelled())throw new AssertionError("Replacement attempt bypassed settling admission");
            REPORT.addProperty("heldAdmissionRefused",true);
            release();phase=3;
        }else if(phase==3 && reservations==0) {
            if(original.isCancelled())throw new AssertionError("Native future was cancelled by adapter");
            REPORT.addProperty("nativeFutureUncancelled",true);REPORT.addProperty("lateResultSettled",true);
            if(Boolean.getBoolean("neighbors.probe.adqLogout"))AdqSearchProbe.submitLifecycleActor();
            else type.getMethod("generateNewQuestAsync",ServerLevel.class).invoke(null,server.overworld());
            REPORT.addProperty("successorSubmitted",true);REPORT.addProperty("passed",true);phase=4;
        }
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    private static void release(){original.whenComplete((value,error)->{if(error!=null)held.completeExceptionally(error);else ((CompletableFuture)held).complete(value);});}
    public static JsonObject report(){return REPORT.deepCopy();}
}
