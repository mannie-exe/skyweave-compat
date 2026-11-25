/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.clientprobe.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import design.inherent.skyweavecompat.clientprobe.LightCacheHandoffWitness;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;

@Mixin(LevelLightEngine.class)
abstract class LightCacheCheckMixin {
    @Shadow @Final protected LevelHeightAccessor levelHeightAccessor;
    @WrapMethod(method="checkBlock")
    private void checked(BlockPos position,Operation<Void> original) {
        var engine=(LevelLightEngine)(Object)this;
        if(!LightCacheHandoffWitness.defer(engine,levelHeightAccessor,position))original.call(position);
    }
}
