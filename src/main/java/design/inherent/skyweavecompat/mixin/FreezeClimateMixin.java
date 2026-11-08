package design.inherent.skyweavecompat.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import design.inherent.skyweavecompat.climate.IslandClimate;
import design.inherent.skyweavecompat.climate.SurfaceClimateScope;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Covers nested mod freezing helpers while leaving physical checks at the real position. */
@Mixin(Biome.class)
public abstract class FreezeClimateMixin {
    @WrapMethod(method = "shouldFreeze(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Z)Z")
    private boolean freezing(LevelReader level, BlockPos pos, boolean edge, Operation<Boolean> original) {
        return SurfaceClimateScope.query(IslandClimate.applies(level), () -> original.call(level, pos, edge));
    }

    @WrapMethod(method = "shouldSnow")
    private boolean snow(LevelReader level, BlockPos pos, Operation<Boolean> original) {
        return SurfaceClimateScope.query(IslandClimate.applies(level), () -> original.call(level, pos));
    }

    @ModifyVariable(method = "warmEnoughToRain", at = @At("HEAD"), argsOnly = true)
    private BlockPos temperature(BlockPos pos) {
        return SurfaceClimateScope.active() ? IslandClimate.temperaturePosition(pos) : pos;
    }
}
