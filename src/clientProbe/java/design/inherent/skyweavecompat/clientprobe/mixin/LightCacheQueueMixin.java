/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.clientprobe.mixin;

import design.inherent.skyweavecompat.clientprobe.InitialLightPacketProbe;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
abstract class LightCacheQueueMixin {
    @Inject(method="queueLightUpdate",at=@At("HEAD"),cancellable=true,require=1,allow=1)
    private void held(Runnable original,CallbackInfo callback) {
        if(InitialLightPacketProbe.holding){InitialLightPacketProbe.callbacks.add(original);callback.cancel();}
    }
}
