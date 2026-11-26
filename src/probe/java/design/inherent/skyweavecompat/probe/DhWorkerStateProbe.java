/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.probe;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.config.types.ConfigEntry;
import com.seibel.distanthorizons.core.util.threading.PriorityTaskPicker;
import com.seibel.distanthorizons.core.util.threading.ThreadPoolUtil;
import java.util.ArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;

/** Inspect the original retained native executor objects; never stop or clear their work. */
public final class DhWorkerStateProbe {
    private static final java.util.concurrent.ConcurrentLinkedQueue<JsonObject> EVENTS = new java.util.concurrent.ConcurrentLinkedQueue<>();
    public static void event(PriorityTaskPicker.Executor executor, String phase) {
        var row = new JsonObject();
        row.addProperty("executorIdentity", System.identityHashCode(executor));
        row.addProperty("phase", phase); row.addProperty("shutdown", executor.isShutdown());
        row.addProperty("thread", Thread.currentThread().getName());
        String caller = StackWalker.getInstance().walk(frames -> frames.limit(12).map(Object::toString).collect(java.util.stream.Collectors.joining("\n")));
        row.addProperty("caller", caller);
        if (EVENTS.size() < 64) EVENTS.add(row);
    }
    private record Entry(String role, PriorityTaskPicker.Executor executor) {}
    private final ArrayList<Entry> entries = new ArrayList<>();
    private Object picker;

    void capture() throws ReflectiveOperationException {
        add("network compression", ThreadPoolUtil.getNetworkCompressionExecutor());
        add("file IO", ThreadPoolUtil.getFileHandlerExecutor());
        add("render loader", ThreadPoolUtil.getRenderLoadingExecutor());
        add("LOD builder", ThreadPoolUtil.getChunkToLodBuilderExecutor());
        add("update propagator", ThreadPoolUtil.getUpdatePropagatorExecutor());
        add("world generation", ThreadPoolUtil.getWorldGenExecutor());
        var field = ThreadPoolUtil.class.getDeclaredField("taskPicker"); field.setAccessible(true);
        picker = field.get(null);
        if (entries.size() != 6 || picker == null) throw new AssertionError("Pinned native DH worker group is incomplete");
    }
    private void add(String role, PriorityTaskPicker.Executor executor) {
        if (executor != null) entries.add(new Entry(role, executor));
    }

    JsonObject afterStop() throws ReflectiveOperationException {
        try { return snapshotAfterStop(); }
        finally { entries.clear(); picker = null; EVENTS.clear(); }
    }

    private JsonObject snapshotAfterStop() throws ReflectiveOperationException {
        var result = new JsonObject(); var rows = new JsonArray();
        var listenerField = ConfigEntry.class.getDeclaredField("listenerList"); listenerField.setAccessible(true);
        var listenerList = (ArrayList<?>)listenerField.get(Config.Common.MultiThreading.numberOfThreads);
        Object[] listeners;
        synchronized (listenerList) { listeners = listenerList.toArray(); }
        var currentPickerField = ThreadPoolUtil.class.getDeclaredField("taskPicker"); currentPickerField.setAccessible(true);
        result.addProperty("sameCurrentPicker", currentPickerField.get(null) == picker);
        var events = new JsonArray(); EVENTS.forEach(events::add); result.add("nativeExecutorEvents", events); EVENTS.clear();
        int registered = 0, queued = 0; boolean retired = true;
        for (var entry : entries) {
            var executor = entry.executor(); var row = new JsonObject();
            boolean retainedListener = false;
            for (var listener : listeners) if (listener == executor) { retainedListener = true; break; }
            int queue = executor.getQueueSize(), running = executor.getRunningTaskCount();
            row.addProperty("role", entry.role()); row.addProperty("executorIdentity", System.identityHashCode(executor)); row.addProperty("shutdown", executor.isShutdown());
            row.addProperty("terminated", executor.isTerminated()); row.addProperty("running", running);
            row.addProperty("nativePendingQueue", queue); row.addProperty("registeredConfigListener", retainedListener);
            rows.add(row); if (retainedListener) registered++; queued += queue;
            retired &= executor.isShutdown() && executor.isTerminated() && running == 0 && queue == 0 && !retainedListener;
        }
        var scheduledField = PriorityTaskPicker.class.getDeclaredField("scheduledFutureRef"); scheduledField.setAccessible(true);
        Object value = ((AtomicReference<?>)scheduledField.get(picker)).get();
        boolean pendingCallback = value instanceof ScheduledFuture<?> future && !future.isDone();
        result.add("executors", rows); result.addProperty("retainedExecutorListeners", registered);
        result.addProperty("retainedNativeQueueEntries", queued); result.addProperty("scheduledPickerCallbackPending", pendingCallback);
        result.addProperty("retired", retired && !pendingCallback);
        result.add("nativeLifecycleControls", lifecycleControls(listenerList));
        result.addProperty("scope", "Actual six native executors captured at ServerStarted and inspected after native ServerStopped; identity-based listener membership and own picker callback; observer releases references after snapshot, no forced queue cleanup or heap/GC claim");
        return result;
    }

    private static JsonObject lifecycleControls(ArrayList<?> listenerList) throws ReflectiveOperationException {
        var result = new JsonObject();
        var parent = new PriorityTaskPicker();
        var executor = parent.createExecutor("Skyweave disposable lifecycle control");
        var poolField = PriorityTaskPicker.Executor.class.getDeclaredField("threadPoolExecutor"); poolField.setAccessible(true);
        Object firstPool = poolField.get(executor);
        executor.onConfigValueSet();
        result.addProperty("activeConfigurationStillReplacesPool", poolField.get(executor) != firstPool && !executor.isShutdown());
        var started = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CompletableFuture<String>();
        var completion = executor.submit(() -> { started.countDown(); return release.join(); });
        var failure = new AtomicReference<Throwable>();
        var closer = new Thread(() -> { try { parent.shutdownNow(); } catch (Throwable error) { failure.set(error); } }, "Skyweave disposable DH closer");
        var configurer = new Thread(() -> {
            try { Config.Common.MultiThreading.numberOfThreads.setWithoutSaving(Config.Common.MultiThreading.numberOfThreads.get()); }
            catch (Throwable error) { failure.set(error); }
        }, "Skyweave disposable DH config dispatch");
        boolean done = false;
        try {
            if (!started.await(2, java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("Native task did not begin");
            closer.start(); configurer.start();
            // Work already running has its native completion dependency fulfilled, never cancelled.
            release.complete("completed native work");
            closer.join(3000); configurer.join(3000);
            if (closer.isAlive() || configurer.isAlive() || failure.get() != null)
                throw new AssertionError("Native close/config dispatch failed or deadlocked", failure.get());
            result.addProperty("runningTaskPreserved", completion.get(1, java.util.concurrent.TimeUnit.SECONDS).equals("completed native work"));
            Object closedPool = poolField.get(executor);
            executor.onConfigValueSet(); // A callback captured before retirement must also be harmless.
            result.addProperty("lateCallbackKeepsClosedPool", closedPool == poolField.get(executor) && executor.isShutdown());
            boolean rejected = false;
            try { executor.execute(() -> {}); } catch (java.util.concurrent.RejectedExecutionException expected) { rejected = true; }
            result.addProperty("postRetirementSubmissionRejected", rejected);
            boolean registered = false;
            synchronized (listenerList) { for (Object listener : listenerList) registered |= listener == executor; }
            result.addProperty("ownListenerDetached", !registered);
            result.addProperty("nativeTerminated", executor.isTerminated());
            done = true;
        } catch (Exception error) { throw new AssertionError("Native lifecycle control failed", error); }
        finally {
            release.complete("completed native work");
            // Only this explicitly created control's native parent is ours to close.
            if (!done || !executor.isShutdown()) parent.shutdownNow();
        }
        boolean passed = true;
        for (var entry : result.entrySet()) passed &= entry.getValue().getAsBoolean();
        result.addProperty("passed", passed);
        return result;
    }
}
