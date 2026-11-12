package design.inherent.skyweavecompat.mixin.water;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import design.inherent.skyweavecompat.climate.IslandClimate;
import design.inherent.skyweavecompat.climate.SurfaceClimateScope;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "com.AdamNew.dynamicwaters.shore.WaterPlants", remap = false)
public abstract class WaterPlantClimateMixin {
    @WrapOperation(method = "frozen", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/biome/Biome;coldEnoughToSnow(Lnet/minecraft/core/BlockPos;)Z"))
    private static boolean cold(Biome biome, BlockPos pos, Operation<Boolean> original) {
        return original.call(biome,SurfaceClimateScope.active() ? IslandClimate.temperaturePosition(pos) : pos);
    }
}
