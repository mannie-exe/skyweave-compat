/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.clientprobe;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.seibel.distanthorizons.core.multiplayer.client.AbstractFullDataNetworkRequestQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/** Passive native request-close and wire-decode observation; no cancellation or retained payloads. */
final class ClientDhNetwork {
    private record Queue(int number, WeakReference<AbstractFullDataNetworkRequestQueue> owner, AtomicInteger calls) {}
    record Call(Queue queue, int number, long start, boolean hadPrior, WeakReference<CompletableFuture<Void>> prior, String kind, boolean interrupt, int waitingBefore, int nativeInProgressBefore, JsonObject stateBefore) {}
    private final ArrayList<Queue> queues = new ArrayList<>();
    private final ConcurrentLinkedQueue<JsonObject> events = new ConcurrentLinkedQueue<>();
    private final AtomicInteger eventRecords = new AtomicInteger();
    final AtomicInteger splitMessages = new AtomicInteger(), decodedPayloads = new AtomicInteger();
    final AtomicInteger lateSubmissions = new AtomicInteger();
    final AtomicInteger latePendingReturns = new AtomicInteger(), lateCancelledReturns = new AtomicInteger();
    private final AtomicInteger delayedSamples = new AtomicInteger();
    void lateSubmission(CompletableFuture<?> future) {
        if(!future.isDone()) latePendingReturns.incrementAndGet();
        if(future.isCancelled()) lateCancelledReturns.incrementAndGet();
        if(lateSubmissions.incrementAndGet()>8) return;
        var row=new JsonObject(); row.addProperty("event","native request submitted after close started");
        String caller=StackWalker.getInstance().walk(frames->frames.limit(12).map(Object::toString).collect(java.util.stream.Collectors.joining("\n")));
        row.addProperty("caller",caller);
        row.addProperty("futureAlreadyDone",future.isDone()); row.addProperty("futureCancelled",future.isCancelled()); publish(row);
    }
    private void publish(JsonObject row) { if(eventRecords.getAndIncrement()<256) events.add(row); }

    synchronized Call closing(AbstractFullDataNetworkRequestQueue owner, CompletableFuture<Void> prior, boolean interrupt) {
        Queue found = null;
        for (var queue : queues) if (queue.owner().get() == owner) { found = queue; break; }
        if (found == null) { found = new Queue(queues.size()+1, new WeakReference<>(owner), new AtomicInteger()); queues.add(found); }
        return new Call(found, found.calls().incrementAndGet(), System.nanoTime(), prior != null, new WeakReference<>(prior), owner.getClass().getName(), interrupt, owner.getWaitingTaskCount(), owner.getInProgressTaskCount(), state(owner));
    }

    /** Weakly consistent counters only; never call native scheduling or cancellation. */
    private static JsonObject state(AbstractFullDataNetworkRequestQueue owner) {
        var row = new JsonObject();
        try {
            var field = AbstractFullDataNetworkRequestQueue.class.getDeclaredField("waitingTasksBySectionPos"); field.setAccessible(true);
            var map = (java.util.Map<?,?>)field.get(owner);
            var taskType = Class.forName(AbstractFullDataNetworkRequestQueue.class.getName()+"$NetRequestTask",false,AbstractFullDataNetworkRequestQueue.class.getClassLoader());
            var retrievalField = taskType.getDeclaredField("future"); retrievalField.setAccessible(true);
            var wireField = taskType.getDeclaredField("networkDataSourceFuture"); wireField.setAccessible(true);
            int waiting = 0, requested = 0, requestDone = 0, wireDone = 0, wireCancelled = 0, wireFailed = 0;
            for (var value : map.values()) {
                waiting++;
                if (((CompletableFuture<?>)retrievalField.get(value)).isDone()) requestDone++;
                var wire = (CompletableFuture<?>)wireField.get(value);
                if (wire != null) {
                    requested++;
                    switch (wire.state()) {
                        case SUCCESS -> wireDone++;
                        case FAILED -> { wireDone++; wireFailed++; }
                        case CANCELLED -> { wireDone++; wireCancelled++; }
                        default -> { }
                    }
                }
            }
            row.addProperty("sampledMapEntries",waiting); row.addProperty("retrievalFuturesDone",requestDone);
            row.addProperty("withWireFuture",requested); row.addProperty("wireFuturesDone",wireDone);
            row.addProperty("wireFuturesCancelled",wireCancelled); row.addProperty("wireFuturesFailed",wireFailed);
            var executor = com.seibel.distanthorizons.core.util.threading.ThreadPoolUtil.getNetworkCompressionExecutor();
            if (executor != null) {
                row.addProperty("compressionQueued",executor.getQueueSize()); row.addProperty("compressionRunning",executor.getRunningTaskCount());
                row.addProperty("compressionShutdown",executor.isShutdown()); row.addProperty("compressionTerminated",executor.isTerminated());
            }
        } catch (ReflectiveOperationException failure) { row.addProperty("inspectionFailure",failure.toString()); }
        return row;
    }

    void requested(Call call, CompletableFuture<Void> future) {
        var row = new JsonObject(); row.addProperty("queueGeneration", call.queue().number()); row.addProperty("call", call.number());
        row.addProperty("priorClosePresent", call.hadPrior()); row.addProperty("returnedSamePriorFuture", call.prior().get() == future);
        row.addProperty("queueClass",call.kind()); row.addProperty("interruptRequested",call.interrupt());
        row.addProperty("waitingBeforeCall",call.waitingBefore()); row.addProperty("nativeSemaphoreReportedInProgressBeforeCall",call.nativeInProgressBefore());
        row.add("nativeStateBeforeCall",call.stateBefore());
        row.addProperty("futureAlreadyDone", future.isDone()); row.addProperty("event", "native close requested"); publish(row);
        if (delayedSamples.incrementAndGet() <= 8) {
            var sample = new Thread(() -> {
                try { Thread.sleep(250); } catch (InterruptedException ignored) { return; }
                var owner = call.queue().owner().get();
                if (future.isDone() || owner == null) return;
                var delayed = new JsonObject(); delayed.addProperty("event","pending native close diagnostic");
                delayed.addProperty("queueGeneration",call.queue().number()); delayed.addProperty("call",call.number());
                delayed.add("nativeState",state(owner)); var threads = new JsonArray();
                Thread.getAllStackTraces().entrySet().stream().filter(entry -> entry.getKey() != Thread.currentThread()
                        && (entry.getKey().getName().contains("DH-") || entry.getKey().getName().equals("Render thread")
                        || java.util.Arrays.stream(entry.getValue()).anyMatch(frame -> frame.getClassName().startsWith("com.seibel."))))
                        .sorted(java.util.Comparator.comparing(entry -> entry.getKey().getName())).limit(64).forEach(entry -> {
                            var thread = new JsonObject(); thread.addProperty("name",entry.getKey().getName());
                            thread.addProperty("state",entry.getKey().getState().name());
                            thread.addProperty("stack",java.util.Arrays.stream(entry.getValue()).limit(32).map(Object::toString).collect(java.util.stream.Collectors.joining("\n")));
                            threads.add(thread);
                        });
                delayed.add("nativeThreadStacks",threads); publish(delayed);
            },"compat passive native network-close sample"); sample.setDaemon(true); sample.start();
        }
        future.whenComplete((ignored, failure) -> {
            var done = new JsonObject(); done.addProperty("queueGeneration", call.queue().number()); done.addProperty("call", call.number());
            done.addProperty("event", "native close future completed"); done.addProperty("elapsedNanos", System.nanoTime()-call.start());
            done.addProperty("exceptional", failure != null);
            var owner = call.queue().owner().get();
            if (owner != null) {
                done.addProperty("waitingRequests", owner.getWaitingTaskCount());
                done.add("nativeState",state(owner));
                // Native shutdown acquires all permits; this number alone is not live work.
                done.addProperty("nativeSemaphoreReportedInProgress", owner.getInProgressTaskCount());
            }
            publish(done);
        });
    }

    JsonObject snapshot() {
        var result = new JsonObject(); var calls = new JsonArray(); events.forEach(calls::add);
        result.add("closeEvents", calls); result.addProperty("actualSplitMessages", splitMessages.get());
        result.addProperty("successfulNativeDtoDecodes", decodedPayloads.get());
        result.addProperty("lateSubmissionsAfterCloseStarted",lateSubmissions.get());
        result.addProperty("latePendingReturns",latePendingReturns.get()); result.addProperty("lateCancelledReturns",lateCancelledReturns.get());
        result.addProperty("omittedLateSubmissionDetails",Math.max(0,lateSubmissions.get()-8));
        result.addProperty("omittedEventRecords",Math.max(0,eventRecords.get()-256));
        result.addProperty("scope", "Actual queue identities/futures and process-total native wire decoder returns; no per-world provenance inference, payload retention, cancellation, or timeout change");
        return result;
    }
}
