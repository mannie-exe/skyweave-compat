/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.clientprobe.mixin;

import design.inherent.skyweavecompat.clientprobe.LightCacheHandoffWitness;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.BlockLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockLightEngine.class)
abstract class LightCacheEmissionMixin {
    @Inject(method="getEmission",at=@At("RETURN"),require=1,allow=1)
    private void emission(long position,BlockState state,CallbackInfoReturnable<Integer> result) {
        if(position==BlockPos.asLong(-27,-59,-62))
            LightCacheHandoffWitness.emission((LightCacheSectionAccess)((LightCacheEngineAccess)this).skyweave$storage(),position,result.getReturnValueI());
    }
}
