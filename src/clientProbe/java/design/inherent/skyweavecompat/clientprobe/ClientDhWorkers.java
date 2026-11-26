/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.clientprobe;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.config.types.ConfigEntry;
import com.seibel.distanthorizons.core.util.threading.PriorityTaskPicker;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;

/** Weak observations of actual shared picker generations; no world ownership inferred from phase. */
final class ClientDhWorkers {
    private record Group(int number, WeakReference<PriorityTaskPicker> picker,
            ArrayList<WeakReference<PriorityTaskPicker.Executor>> executors) {}
    private final ArrayList<Group> groups = new ArrayList<>();

    synchronized int capture(PriorityTaskPicker picker) throws ReflectiveOperationException {
        for (var group : groups) if (group.picker().get() == picker) return group.number();
        var field = PriorityTaskPicker.class.getDeclaredField("executors"); field.setAccessible(true);
        var executors = new ArrayList<WeakReference<PriorityTaskPicker.Executor>>();
        for (Object value : (ArrayList<?>)field.get(picker))
            executors.add(new WeakReference<>((PriorityTaskPicker.Executor)value));
        if (executors.size() != 6) throw new AssertionError("Pinned DH shared picker must have six executors");
        int number = groups.size() + 1; groups.add(new Group(number, new WeakReference<>(picker), executors));
        return number;
    }

    synchronized JsonObject snapshot() throws ReflectiveOperationException {
        var result = new JsonObject(); var rows = new JsonArray(); boolean retired = !groups.isEmpty();
        var listenerField = ConfigEntry.class.getDeclaredField("listenerList"); listenerField.setAccessible(true);
        var listeners = (ArrayList<?>)listenerField.get(Config.Common.MultiThreading.numberOfThreads);
        Object[] registered;
        synchronized (listeners) { registered = listeners.toArray(); }
        var scheduledField = PriorityTaskPicker.class.getDeclaredField("scheduledFutureRef"); scheduledField.setAccessible(true);
        for (var group : groups) {
            var row = new JsonObject(); row.addProperty("sharedPickerGeneration", group.number());
            var picker = group.picker().get(); row.addProperty("pickerWeaklyCleared", picker == null);
            boolean callback = picker != null && ((AtomicReference<?>)scheduledField.get(picker)).get() instanceof ScheduledFuture<?> future && !future.isDone();
            row.addProperty("scheduledCallbackPending", callback);
            int live = 0, running = 0, queued = 0, retainedListeners = 0, terminated = 0;
            for (var reference : group.executors()) {
                var executor = reference.get(); if (executor == null) continue;
                live++; running += executor.getRunningTaskCount(); queued += executor.getQueueSize();
                if (executor.isShutdown() && executor.isTerminated()) terminated++;
                for (var listener : registered) if (listener == executor) { retainedListeners++; break; }
            }
            boolean done = !callback && running == 0 && queued == 0 && retainedListeners == 0 && live == terminated;
            row.addProperty("liveExecutorObjects", live); row.addProperty("terminatedExecutors", terminated);
            row.addProperty("running", running); row.addProperty("nativePendingQueue", queued);
            row.addProperty("registeredConfigListeners", retainedListeners); row.addProperty("retired", done);
            retired &= done; rows.add(row);
        }
        result.add("sharedPickers", rows); result.addProperty("retired", retired);
        result.addProperty("scope", "Actual native close identities; weak picker/executor references, no forced queue/scheduler cleanup or GC assertion");
        return result;
    }
}
