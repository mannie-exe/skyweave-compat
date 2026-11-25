/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.mixin.client;

import design.inherent.skyweavecompat.lighting.InitialClientLight;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public abstract class InitialLightLevelMixin {
    @Inject(method="unload",at=@At("HEAD"),require=1,allow=1)
    private void skyweave$forget(LevelChunk chunk,CallbackInfo callback){InitialClientLight.forget((ClientLevel)(Object)this,chunk);}
    @Inject(method="disconnect",at=@At("HEAD"),require=1,allow=1)
    private void skyweave$retire(CallbackInfo callback){InitialClientLight.retire((ClientLevel)(Object)this);}
}
