/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.mixin.dh;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.config.listeners.IConfigListener;
import com.seibel.distanthorizons.core.util.threading.PriorityTaskPicker;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Retire this executor's config subscription without changing native task execution or waits. */
@Mixin(value=PriorityTaskPicker.Executor.class, remap=false)
abstract class ExecutorLifecycleMixin {
    @Unique private boolean skyweave$retired;

    @WrapMethod(method="onConfigValueSet")
    private void skyweave$configure(Operation<Void> original) {
        synchronized (this) {
            if (!skyweave$retired) original.call();
        }
    }

    @WrapMethod(method="execute")
    private void skyweave$submit(Runnable task, Operation<Void> original) {
        synchronized (this) {
            if (skyweave$retired) throw new RejectedExecutionException("Distant Horizons executor has been shut down");
            original.call(task);
        }
    }

    @WrapMethod(method="shutdown")
    private void skyweave$shutdown(Operation<Void> original) {
        try {
            synchronized (this) { skyweave$retired = true; original.call(); }
        } finally { skyweave$detach(); }
    }

    @WrapMethod(method="shutdownNow")
    private List<Runnable> skyweave$shutdownNow(Operation<List<Runnable>> original) {
        try {
            synchronized (this) { skyweave$retired = true; return original.call(); }
        } finally { skyweave$detach(); }
    }

    @Unique private void skyweave$detach() {
        // Config dispatch holds its listener-list monitor before entering this executor.
        // Release the executor monitor before acquiring that native monitor to avoid inversion.
        Config.Common.MultiThreading.numberOfThreads.removeListener((IConfigListener)this);
    }
}
