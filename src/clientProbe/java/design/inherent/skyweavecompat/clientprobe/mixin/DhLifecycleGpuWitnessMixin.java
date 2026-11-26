/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.clientprobe.mixin;

import com.seibel.distanthorizons.common.render.openGl.glObject.buffer.GLBuffer;
import design.inherent.skyweavecompat.clientprobe.ClientDhLifecycleProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=GLBuffer.class, remap=false)
abstract class DhLifecycleGpuWitnessMixin {
    @Inject(method="destroyBufferIdNow", at=@At(value="INVOKE",
            target="Lcom/seibel/distanthorizons/common/wrappers/minecraft/MinecraftGLWrapper;glDeleteBuffers(I)V",
            shift=At.Shift.AFTER), require=1, allow=1)
    private static void observed(int id, String reason, CallbackInfo ci) {
        ClientDhLifecycleProbe.deletedGpuBuffer(id);
    }
}
