/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.mixin.dh;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.LodBufferContainer;
import com.seibel.distanthorizons.core.render.RenderThreadTaskHandler;
import design.inherent.skyweavecompat.dh.RenderBufferScope;
import java.util.concurrent.CompletableFuture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value=LodBufferContainer.class, remap=false)
abstract class RenderBufferSetupMixin {
    @WrapOperation(method="tryMakeAndUploadBuffersAsync", at=@At(value="INVOKE",
            target="Ljava/util/concurrent/CompletableFuture;thenRun(Ljava/lang/Runnable;)Ljava/util/concurrent/CompletableFuture;", ordinal=0), require=1, allow=1)
    private static CompletableFuture<Void> skyweave$returnOwner(CompletableFuture<?> future, Runnable nativeReturn,
            Operation<CompletableFuture<Void>> original) {
        var scope = RenderBufferScope.currentWorker();
        return original.call(future, scope == null ? nativeReturn : scope.nativeReturnCallback(nativeReturn));
    }
    @WrapOperation(method="tryMakeAndUploadBuffersAsync", at=@At(value="INVOKE",
            target="Lcom/seibel/distanthorizons/core/render/RenderThreadTaskHandler;queueRunningOnRenderThread(Ljava/lang/String;Ljava/lang/Runnable;)V"), require=1, allow=1)
    private static void skyweave$submitted(RenderThreadTaskHandler handler, String name, Runnable task, Operation<Void> original) {
        var scope = RenderBufferScope.currentWorker(); if (scope != null) scope.submissionStarted();
        original.call(handler, name, task);
    }
}
