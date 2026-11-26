/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.clientprobe.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.seibel.distanthorizons.core.multiplayer.client.AbstractFullDataNetworkRequestQueue;
import com.seibel.distanthorizons.core.generation.tasks.DataSourceRetrievalResult;
import design.inherent.skyweavecompat.clientprobe.ClientDhLifecycleProbe;
import java.util.concurrent.CompletableFuture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value=AbstractFullDataNetworkRequestQueue.class, remap=false)
abstract class DhLifecycleNetworkCloseWitnessMixin {
    @Shadow private volatile CompletableFuture<Void> closingFuture;
    @WrapMethod(method="startClosingAsync")
    private CompletableFuture<Void> requested(boolean interrupt, Operation<CompletableFuture<Void>> original) {
        Object observation=ClientDhLifecycleProbe.networkClosing((AbstractFullDataNetworkRequestQueue)(Object)this,closingFuture,interrupt);
        var result=original.call(interrupt); ClientDhLifecycleProbe.networkCloseRequested(observation,result); return result;
    }
    @WrapMethod(method="submitRequest")
    private CompletableFuture<DataSourceRetrievalResult> submitted(long position, Long changedAfter,
            Operation<CompletableFuture<DataSourceRetrievalResult>> original) {
        boolean closing=closingFuture!=null; var result=original.call(position,changedAfter);
        if(closing) ClientDhLifecycleProbe.networkLateSubmission(result); return result;
    }
}
