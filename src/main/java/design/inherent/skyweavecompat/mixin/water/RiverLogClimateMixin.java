package design.inherent.skyweavecompat.mixin.water;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import design.inherent.skyweavecompat.climate.SurfaceClimateScope;
import design.inherent.skyweavecompat.climate.IslandClimate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "com.AdamNew.dynamicwaters.RiverLogs", remap = false)
public abstract class RiverLogClimateMixin {
    @WrapMethod(method = "lay")
    private static void scope(WorldGenLevel level, net.minecraft.world.level.chunk.ChunkAccess chunk, Operation<Void> original) {
        SurfaceClimateScope.run(level, () -> original.call(level, chunk));
    }

    @WrapOperation(method = {"layTrunk", "wood"}, at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/biome/Biome;coldEnoughToSnow(Lnet/minecraft/core/BlockPos;)Z"))
    private static boolean cold(Biome biome, BlockPos pos, Operation<Boolean> original) {
        return original.call(biome,SurfaceClimateScope.active() ? IslandClimate.temperaturePosition(pos) : pos);
    }
}
