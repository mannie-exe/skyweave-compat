/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.mixin.dh;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.seibel.distanthorizons.core.generation.tasks.DataSourceRetrievalResult;
import com.seibel.distanthorizons.core.multiplayer.client.AbstractFullDataNetworkRequestQueue;
import java.util.concurrent.CompletableFuture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Use the native queue's existing close transition as admission authority. */
@Mixin(value=AbstractFullDataNetworkRequestQueue.class, remap=false)
abstract class NetworkRequestLifecycleMixin {
    @Shadow private volatile CompletableFuture<Void> closingFuture;

    @WrapMethod(method="startClosingAsync")
    private CompletableFuture<Void> skyweave$close(boolean interrupt, Operation<CompletableFuture<Void>> original) {
        // Native tick already uses this monitor. Publish close after any admitted
        // dispatch and reuse its actual result, including failure or cancellation.
        synchronized (this) { return closingFuture != null ? closingFuture : original.call(interrupt); }
    }

    @WrapMethod(method="submitRequest")
    private CompletableFuture<DataSourceRetrievalResult> skyweave$submit(long position, Long changedAfter,
            Operation<CompletableFuture<DataSourceRetrievalResult>> original) {
        synchronized (this) {
            if (closingFuture == null) return original.call(position, changedAfter);
            // This new result belongs only to this refused submission. Existing
            // shared data and native request futures are never cancelled here.
            var refused = new CompletableFuture<DataSourceRetrievalResult>();
            refused.cancel(false); return refused;
        }
    }
}
