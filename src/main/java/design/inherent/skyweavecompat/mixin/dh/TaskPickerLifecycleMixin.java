/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.mixin.dh;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.seibel.distanthorizons.core.util.threading.PriorityTaskPicker;
import java.util.ArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Close only this picker's abandoned queues and retry after its original immediate shutdown. */
@Mixin(value=PriorityTaskPicker.class, remap=false)
abstract class TaskPickerLifecycleMixin {
    @Shadow @Final private AtomicBoolean isShutDownRef;
    @Shadow @Final private ReentrantLock taskPickerLock;
    @Shadow @Final private ArrayList<PriorityTaskPicker.Executor> executors;
    @Shadow @Final private AtomicReference<ScheduledFuture<?>> scheduledFutureRef;

    @WrapMethod(method="startNextTask")
    private void skyweave$admit(boolean blocking, Operation<Void> original) {
        // Preserve native blocking/try-lock admission. The original lock is reentrant;
        // checking inside it also fences callers already waiting when close completes.
        if (blocking) taskPickerLock.lock();
        else if (!taskPickerLock.tryLock()) return;
        try {
            if (!isShutDownRef.get()) original.call(blocking);
        } finally { taskPickerLock.unlock(); }
    }

    @WrapMethod(method="shutdownNow")
    private void skyweave$retire(Operation<Void> original) {
        try { original.call(); }
        finally {
            if (isShutDownRef.get()) {
                // Never hold the picker lock during native termination waits: workers need it.
                taskPickerLock.lock();
                try {
                    var retry = scheduledFutureRef.getAndSet(null);
                    if (retry != null) retry.cancel(false);
                    // Native immediate shutdown has already abandoned these undispatched tasks.
                    // Release its private wrappers; do not run them or cancel shared futures.
                    for (var executor : executors) if (executor != null) executor.clearQueue();
                } finally { taskPickerLock.unlock(); }
            }
        }
    }
}
