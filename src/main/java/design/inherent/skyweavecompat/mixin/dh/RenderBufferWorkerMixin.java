/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.mixin.dh;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.LodBufferContainer;
import com.seibel.distanthorizons.core.render.QuadTree.LodRenderSection;
import design.inherent.skyweavecompat.dh.RenderBufferScope;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value=LodRenderSection.class, remap=false)
abstract class RenderBufferWorkerMixin {
    @WrapMethod(method="lambda$uploadRenderDataToGpuAsync$1")
    private void skyweave$loans(CompletableFuture<Void> result, Operation<Void> original) {
        try (var scope = RenderBufferScope.open()) { original.call(result); }
    }
    @WrapOperation(method="lambda$uploadRenderDataToGpuAsync$1", at=@At(value="INVOKE",
            target="Lcom/seibel/distanthorizons/core/render/QuadTree/LodRenderSection;uploadToGpuAsync(Ljava/util/concurrent/CompletableFuture;Ljava/util/ArrayList;Ljava/util/ArrayList;)Ljava/util/concurrent/CompletableFuture;"), require=1, allow=1)
    private CompletableFuture<LodBufferContainer> skyweave$upload(LodRenderSection section, CompletableFuture<Void> result,
            ArrayList<ByteBuffer> opaque, ArrayList<ByteBuffer> transparent, Operation<CompletableFuture<LodBufferContainer>> original) {
        var future = original.call(section, result, opaque, transparent);
        var scope = RenderBufferScope.currentWorker(); if (scope != null) scope.bindUpload(future);
        return future;
    }
}
