package design.inherent.skyweavecompat.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import design.inherent.skyweavecompat.climate.IslandClimate;
import design.inherent.skyweavecompat.climate.SurfaceClimateScope;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import org.sathrek.sky_archipelago.worldgen.generator.surface.SurfaceBlockSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = SurfaceBlockSelector.class, remap = false)
public abstract class SurfaceTemperatureMixin {
    @WrapOperation(method = {"resolveProfile", "forRiverBiome"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/biome/Biome;coldEnoughToSnow(Lnet/minecraft/core/BlockPos;)Z"), require = 2)
    private static boolean snow(Biome biome, BlockPos position, Operation<Boolean> original) {
        return original.call(biome, SurfaceClimateScope.active() ? IslandClimate.temperaturePosition(position) : position);
    }
}
