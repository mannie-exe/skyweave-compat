/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.clientprobe.mixin;

import design.inherent.skyweavecompat.clientprobe.LightCacheHandoffWitness;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
abstract class LightCacheApplyMixin {
    @Shadow private ClientLevel level;
    @Inject(method="applyLightData",at=@At("HEAD"),require=1,allow=1)
    private void applied(int x,int z,ClientboundLightUpdatePacketData data,CallbackInfo callback) {
        if(x==-2 && z==-4)LightCacheHandoffWitness.packet("packet-apply",data,level.getLightEngine().getMinLightSection());
    }
}
