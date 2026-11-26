/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.clientprobe.mixin;

import com.seibel.distanthorizons.core.api.internal.ClientApi;
import design.inherent.skyweavecompat.clientprobe.ClientDhLifecycleProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=ClientApi.class,remap=false)
abstract class DhLifecycleApiFramesMixin {
    @Inject(method="renderLodLayer",at=@At("HEAD"),require=1)
    private void frame(boolean transparent,CallbackInfo ci) {
        ClientDhLifecycleProbe.nativeFrame(transparent ? "dhTransparentEntries" : "dhOpaqueEntries");
    }
}
