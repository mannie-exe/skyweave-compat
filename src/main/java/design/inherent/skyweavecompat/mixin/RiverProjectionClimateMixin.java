package design.inherent.skyweavecompat.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import design.inherent.skyweavecompat.climate.IslandClimate;
import design.inherent.skyweavecompat.terrain.GeologicalGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

@Mixin(targets = "org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandRiverBiomeProjector", remap = false)
public abstract class RiverProjectionClimateMixin {
    @WrapOperation(method = "sampleQuart", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/biome/Biome;coldEnoughToSnow(Lnet/minecraft/core/BlockPos;)Z"))
    private static boolean cold(Biome biome, BlockPos pos, Operation<Boolean> original,
            @Coerce Object context, int x, int z) {
        var generator = ((ProjectionClimateContext)context).skyweave$climateGenerator();
        return original.call(biome, ((GeologicalGenerator)(Object)generator).skyweave$isEnabled()
                ? design.inherent.skyweavecompat.climate.ClimateRuntime.temperaturePosition(generator,
                    ((ProjectionClimateContext)context).skyweave$climateRandom(),pos) : pos);
    }
}
