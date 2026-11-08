package design.inherent.skyweavecompat.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import design.inherent.skyweavecompat.climate.IslandClimate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.feature.SnowAndFreezeFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Air and water can occupy different fuzzy biome cells at the same surface. */
@Mixin(SnowAndFreezeFeature.class)
public abstract class SnowFreezeClimateMixin {
    @WrapOperation(method = "place", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/biome/Biome;shouldFreeze(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Z)Z"))
    private boolean waterBiome(Biome air, LevelReader level, BlockPos water, boolean edge, Operation<Boolean> original) {
        return original.call(IslandClimate.applies(level) ? level.getBiome(water).value() : air, level, water, edge);
    }
}
