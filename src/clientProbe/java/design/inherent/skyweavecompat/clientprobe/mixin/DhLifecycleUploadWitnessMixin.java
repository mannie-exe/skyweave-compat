/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.clientprobe.mixin;

import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.LodBufferContainer;
import com.seibel.distanthorizons.core.level.IDhClientLevel;
import com.seibel.distanthorizons.core.render.QuadTree.LodRenderSection;
import design.inherent.skyweavecompat.clientprobe.ClientDhLifecycleProbe;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value=LodRenderSection.class,remap=false)
abstract class DhLifecycleUploadWitnessMixin {
    @Shadow @Final private IDhClientLevel clientLevel;
    @Inject(method="uploadToGpuAsync",at=@At("RETURN"),require=1,allow=1)
    private void observed(CompletableFuture<Void> source, ArrayList<ByteBuffer> opaque, ArrayList<ByteBuffer> transparent,
            CallbackInfoReturnable<CompletableFuture<LodBufferContainer>> ci) {
        ClientDhLifecycleProbe.uploaded(clientLevel,ci.getReturnValue());
    }
}
