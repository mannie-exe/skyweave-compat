/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.clientprobe.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.llamalad7.mixinextras.sugar.Local;
import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.LodBufferContainer;
import com.seibel.distanthorizons.core.wrapperInterfaces.render.objects.IVertexBufferWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import design.inherent.skyweavecompat.clientprobe.ClientDhLifecycleProbe;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value=LodBufferContainer.class, remap=false)
abstract class DhLifecycleUploadFaultWitnessMixin {
    @Inject(method="tryMakeAndUploadBuffersAsync", at=@At(value="INVOKE",
            target="Lcom/seibel/distanthorizons/core/render/RenderThreadTaskHandler;queueRunningOnRenderThread(Ljava/lang/String;Ljava/lang/Runnable;)V"), require=1, allow=1)
    private static void beforeSubmission(long pos, IClientLevelWrapper level, ArrayList<ByteBuffer> opaque,
            ArrayList<ByteBuffer> transparent, CallbackInfoReturnable<CompletableFuture<LodBufferContainer>> ci,
            @Local(ordinal=0) CompletableFuture<LodBufferContainer> result) {
        ClientDhLifecycleProbe.nativeCpuUpload(result);
    }
    @WrapOperation(method="lambda$uploadBuffersAsync$0", at=@At(value="INVOKE",
            target="Lcom/seibel/distanthorizons/core/wrapperInterfaces/render/objects/IVertexBufferWrapper;uploadVertexBuffer(Ljava/nio/ByteBuffer;I)V"), require=1, allow=1)
    private static void afterUpload(IVertexBufferWrapper buffer, ByteBuffer bytes, int count, Operation<Void> original,
            @Local(argsOnly=true, ordinal=0) CompletableFuture<?> result) {
        original.call(buffer, bytes, count);
        ClientDhLifecycleProbe.afterActualVertexUpload(result, buffer);
    }
}
