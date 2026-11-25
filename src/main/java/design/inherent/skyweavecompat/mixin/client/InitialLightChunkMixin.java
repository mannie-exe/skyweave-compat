/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.mixin.client;

import design.inherent.skyweavecompat.lighting.InitialClientLight;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelChunk.class)
public abstract class InitialLightChunkMixin {
    @Inject(method="replaceWithPacketData",at=@At("HEAD"),require=1,allow=1)
    private void skyweave$decoded(CallbackInfo callback){InitialClientLight.decoded((LevelChunk)(Object)this);}
}
