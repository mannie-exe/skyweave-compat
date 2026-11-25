/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.mixin.client;

import design.inherent.skyweavecompat.lighting.InitialClientLight;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class InitialLightWorldMixin {
    @Shadow public ClientLevel level;
    @Inject(method="setLevel",at=@At("HEAD"),require=1,allow=1)
    private void skyweave$replace(ClientLevel next,ReceivingLevelScreen.Reason reason,CallbackInfo callback) {
        if(level!=null && level!=next)InitialClientLight.retire(level);
    }
}
