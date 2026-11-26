/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.clientprobe.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.seibel.distanthorizons.core.level.IDhClientLevel;
import com.seibel.distanthorizons.core.render.QuadTree.LodRenderSection;
import design.inherent.skyweavecompat.clientprobe.ClientDhLifecycleProbe;
import java.util.concurrent.CompletableFuture;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value=LodRenderSection.class, remap=false)
abstract class DhLifecycleCpuFrameWitnessMixin {
    @Shadow @Final private IDhClientLevel clientLevel;
    @WrapMethod(method="lambda$uploadRenderDataToGpuAsync$1")
    private void observed(CompletableFuture<Void> result, Operation<Void> original) {
        long id = ClientDhLifecycleProbe.enterCpuFrame(clientLevel);
        try { original.call(result); }
        finally { ClientDhLifecycleProbe.leaveCpuFrame(id); }
    }
    @WrapOperation(method="lambda$uploadRenderDataToGpuAsync$1", at=@At(value="INVOKE",
            target="Ljava/util/concurrent/CompletableFuture;join()Ljava/lang/Object;"), require=1, allow=1)
    private Object completed(CompletableFuture<?> future, Operation<Object> original) {
        Object value = original.call(future); ClientDhLifecycleProbe.afterActualCpuJoin(value); return value;
    }
}
