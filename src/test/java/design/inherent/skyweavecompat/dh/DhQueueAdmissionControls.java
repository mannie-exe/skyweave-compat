/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.dh;

import com.google.gson.JsonObject;
import com.seibel.distanthorizons.core.api.internal.chunkUpdating.ChunkUpdateData;
import com.seibel.distanthorizons.core.pos.DhChunkPos;
import com.seibel.distanthorizons.core.util.threading.PriorityTaskPicker;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.IChunkWrapper;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/** Native queue admission only. Recorded builder children are not executed or claimed as LOD output. */
public final class DhQueueAdmissionControls {
    public static volatile DhChunkPos clientCenter;
    private static CountDownLatch started, release;
    private static volatile Thread nativeWorker;

    static void reset() {
        started=new CountDownLatch(1);release=new CountDownLatch(1);nativeWorker=null;
        clientCenter=new DhChunkPos(0,0);
    }

    public static Runnable gate(Runnable original) {
        return () -> {
            nativeWorker=Thread.currentThread();started.countDown();
            try { release.await(); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            original.run();
        };
    }

    static void verify(Object manager, ExecutorService workerExecutor, boolean patched, boolean client) throws Exception {
        if (!started.await(2,TimeUnit.SECONDS)) throw new AssertionError("Original native executor did not start its loop");
        var type=manager.getClass();var add=type.getMethod("addItemToUpdateQueue",DhChunkPos.class,ChunkUpdateData.class);
        var clear=type.getMethod("clear");var center=type.getMethod("setCenter",DhChunkPos.class);
        var contains=type.getMethod("contains",DhChunkPos.class);var count=type.getMethod("getQueuedCount");
        var queue=type.getField("updateQueue").get(manager);
        var entriesField=queue.getClass().getDeclaredField("updateDataByChunkPos");entriesField.setAccessible(true);
        @SuppressWarnings("unchecked") var entries=(java.util.Map<DhChunkPos,ChunkUpdateData>)entriesField.get(queue);
        var result=new JsonObject();result.addProperty("patched",patched);
        result.addProperty("scope","Original native queues/worker; recording/rejecting builder admission, no fabricated or completed LOD data");
        var clientField=type.getDeclaredField("MC_CLIENT");clientField.setAccessible(true);
        boolean actualClient=clientField.get(null)!=null;
        if (actualClient!=client) throw new AssertionError("Native manager selected a different platform branch");
        result.addProperty("nativeClientWrapperPresent",actualClient);
        var pools=Class.forName("com.seibel.distanthorizons.core.util.threading.ThreadPoolUtil");
        Field builder=pools.getDeclaredField("chunkToLodBuilderThreadPool");builder.setAccessible(true);
        Object previous=builder.get(null);var picker=new PriorityTaskPicker();var target=new RecordingExecutor(picker);
        try {
            if ((int)count.invoke(manager)!=0) throw new AssertionError("Native manager did not start empty");
            center.invoke(manager,clientCenter);
            type.getField("maxSize").setInt(manager,3);
            // Deterministic native insertion/capacity while its original worker is held.
            var a=data(16,"a");var replacement=data(16,"replacement");var far=data(32,"far");
            var near=data(1,"near");var incoming=data(48,"incoming");
            add.invoke(manager,a.chunkWrapper.getChunkPos(),a);add.invoke(manager,far.chunkWrapper.getChunkPos(),far);
            add.invoke(manager,replacement.chunkWrapper.getChunkPos(),replacement);
            // Native ChunkPosQueue keeps its first entry; the manager's separate wrapper cache takes the newest.
            if ((int)count.invoke(manager)!=2 || entries.get(new DhChunkPos(16,0))!=a
                    || type.getMethod("tryGetChunk",DhChunkPos.class).invoke(manager,a.chunkWrapper.getChunkPos())!=replacement.chunkWrapper)
                throw new AssertionError("Native duplicate/coalescing contract changed");
            add.invoke(manager,near.chunkWrapper.getChunkPos(),near);add.invoke(manager,incoming.chunkWrapper.getChunkPos(),incoming);
            if ((int)count.invoke(manager)!=3 || (boolean)contains.invoke(manager,far.chunkWrapper.getChunkPos())
                    || !(boolean)contains.invoke(manager,incoming.chunkWrapper.getChunkPos()))
                throw new AssertionError("Native capacity/furthest removal changed");
            result.addProperty("duplicateAndCapacity",true);clear.invoke(manager);
            if ((int)count.invoke(manager)!=0) throw new AssertionError("Native clear did not empty both queues");
            result.addProperty("clearBeforeStart",true);
            type.getField("maxSize").setInt(manager,500);

            var due=data(3,"due");long originalTime=due.firstQueuedNanoTime;
            var blocker=data(200,"blocker");var left=data(10,"left");var right=data(100,"right");
            builder.set(null,null);add.invoke(manager,due.chunkWrapper.getChunkPos(),due);release.countDown();
            long debounce=type.getField("UPDATE_DEBOUNCE_NANOS").getLong(null);
            if (debounce<0 || debounce>TimeUnit.SECONDS.toNanos(5)) throw new AssertionError("Unexpected pinned native debounce");
            long until=due.firstQueuedNanoTime+debounce+TimeUnit.MILLISECONDS.toNanos(60);
            while (System.nanoTime()<until) TimeUnit.MILLISECONDS.sleep(2);
            if (!target.accepted.isEmpty() || due.firstQueuedNanoTime!=originalTime || !nativeWorker.isAlive())
                throw new AssertionError("Unavailable executor lost input or native worker");
            result.addProperty("unavailableRetainsOriginalAge",true);
            target.reject=true;builder.set(null,target);center.invoke(manager,clientCenter);
            await(() -> target.attempts.get()>0,"Native due update never reached rejecting executor");
            if (!target.accepted.isEmpty()) throw new AssertionError("Rejected dispatch was treated as accepted");
            target.reject=false;center.invoke(manager,clientCenter);
            await(() -> target.accepted.size()==1,"Due update did not progress after executor admission");
            if (target.accepted.getFirst()!=due || due.firstQueuedNanoTime!=originalTime)
                throw new AssertionError("Retry replaced the original native data or age");
            result.addProperty("rejectionThenAcceptedSameNativeData",true);

            // Hold one real execute boundary while native ordering is changed externally.
            var hold=new DispatchGate();target.nextGate=hold;
            add.invoke(manager,blocker.chunkWrapper.getChunkPos(),blocker);
            if (!hold.entered.await(2,TimeUnit.SECONDS)) throw new AssertionError("Native dispatch boundary was not reached");
            add.invoke(manager,left.chunkWrapper.getChunkPos(),left);add.invoke(manager,right.chunkWrapper.getChunkPos(),right);
            clientCenter=new DhChunkPos(100,0);center.invoke(manager,clientCenter);hold.release.countDown();
            await(() -> target.accepted.size()==4,"Native nearer/center updates did not drain");
            if (target.accepted.get(1)!=blocker || target.accepted.get(2)!=right || target.accepted.get(3)!=left)
                throw new AssertionError("Native center/closest ordering changed");
            result.addProperty("newNearerAndExternalCenterOrder",true);

            var clearing=data(201,"clearing");var dropped=data(202,"dropped");
            var clearGate=new DispatchGate();target.nextGate=clearGate;
            add.invoke(manager,clearing.chunkWrapper.getChunkPos(),clearing);
            if (!clearGate.entered.await(2,TimeUnit.SECONDS)) throw new AssertionError("Concurrent clear never reached native dispatch");
            add.invoke(manager,dropped.chunkWrapper.getChunkPos(),dropped);clear.invoke(manager);
            if ((int)count.invoke(manager)!=0) throw new AssertionError("Clear retained queued work during an in-flight dispatch");
            clearGate.release.countDown();
            await(() -> target.accepted.size()==5,"Clear incorrectly discarded an already in-flight native dispatch");
            TimeUnit.MILLISECONDS.sleep(75);
            if (target.accepted.get(4)!=clearing || target.accepted.contains(dropped) || (int)count.invoke(manager)!=0)
                throw new AssertionError("Concurrent clear changed native queued/in-flight ownership");
            result.addProperty("clearPreservesInFlightAndDropsQueued",true);
            var following=data(203,"following");add.invoke(manager,following.chunkWrapper.getChunkPos(),following);
            await(() -> target.accepted.size()==6,"Native worker did not accept new work after clear");
            if (target.accepted.get(5)!=following) throw new AssertionError("Wrong native data after clear");
            result.addProperty("progressAfterClear",true);

            target.reject=true;var closeGate=new DispatchGate();target.nextGate=closeGate;var closing=data(7,"closing");
            add.invoke(manager,closing.chunkWrapper.getChunkPos(),closing);
            if (!closeGate.entered.await(2,TimeUnit.SECONDS)) throw new AssertionError("Close control never reached native dispatch");
            type.getMethod("close").invoke(manager);
            if (!workerExecutor.awaitTermination(2,TimeUnit.SECONDS)) throw new AssertionError("Native close did not terminate worker");
            int accepted=target.accepted.size();center.invoke(manager,new DhChunkPos(0,0));
            add.invoke(manager,closing.chunkWrapper.getChunkPos(),closing);clear.invoke(manager);
            TimeUnit.MILLISECONDS.sleep(75);
            if (nativeWorker.isAlive() || target.accepted.size()!=accepted || !target.interruptedAtExecute)
                throw new AssertionError("Post-close notifications revived native work");
            result.addProperty("closeAndPostCloseNotifications",true);
            result.addProperty("acceptedNativeChildren",target.accepted.size());
            System.out.println("DH_ADMISSION_OBSERVATION "+result);
        } finally {
            release.countDown();if (target.nextGate!=null) target.nextGate.release.countDown();
            target.releaseHeld();builder.set(null,previous);target.shutdownNow();picker.shutdownNow();
        }
    }

    private static ChunkUpdateData data(int x,String label) {
        var position=new DhChunkPos(x,0);
        var chunk=(IChunkWrapper)Proxy.newProxyInstance(IChunkWrapper.class.getClassLoader(),new Class<?>[]{IChunkWrapper.class},
                (proxy,method,args) -> switch (method.getName()) {
                    case "getChunkPos" -> position;
                    case "copy" -> proxy;
                    case "toString" -> label;
                    default -> method.getReturnType()==boolean.class ? false : method.getReturnType()==int.class ? 0 : null;
                });
        return new ChunkUpdateData(chunk,null);
    }

    private static void await(BooleanSupplier condition,String failure) throws InterruptedException {
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
        while (!condition.getAsBoolean() && System.nanoTime()<end) TimeUnit.MILLISECONDS.sleep(2);
        if (!condition.getAsBoolean()) throw new AssertionError(failure);
    }

    private static final class DispatchGate {
        final CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
    }

    private static final class RecordingExecutor extends PriorityTaskPicker.Executor {
        final AtomicInteger attempts=new AtomicInteger();
        final List<ChunkUpdateData> accepted=new CopyOnWriteArrayList<>();
        volatile boolean reject;
        volatile boolean interruptedAtExecute;
        volatile DispatchGate nextGate,held;
        RecordingExecutor(PriorityTaskPicker picker) { super(picker,"compat admission recorder",5,() -> true); }
        @Override public void execute(Runnable child) {
            attempts.incrementAndGet();var gate=nextGate;
            if (gate!=null) {
                held=gate;nextGate=null;gate.entered.countDown();
                try { if (!gate.release.await(3,TimeUnit.SECONDS)) throw new AssertionError("Fixture dispatch gate expired"); }
                catch (InterruptedException interrupted) {
                    interruptedAtExecute=true;Thread.currentThread().interrupt();throw new RejectedExecutionException(interrupted);
                }
                finally { held=null; }
            }
            if (reject) throw new RejectedExecutionException("controlled admission");
            var captures=new ArrayList<ChunkUpdateData>();
            for (Field field:child.getClass().getDeclaredFields()) if (field.getType()==ChunkUpdateData.class) {
                field.setAccessible(true);
                try { captures.add((ChunkUpdateData)field.get(child)); }
                catch (IllegalAccessException failure) { throw new AssertionError(failure); }
            }
            if (captures.size()!=1) throw new AssertionError("Native builder child did not retain one original data object");
            accepted.add(captures.getFirst());
        }
        void releaseHeld() { var gate=held;if(gate!=null)gate.release.countDown(); }
    }
}
