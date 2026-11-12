package design.inherent.skyweavecompat.mixin.water;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import design.inherent.skyweavecompat.climate.IslandClimate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Floe creation uses the same temperature datum as subsequent water freezing. */
@Mixin(targets = "com.AdamNew.dynamicwaters.events.IceFloeSpawner", remap = false)
public abstract class FloeClimateMixin {
    @WrapOperation(method = "spawnNear", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/biome/Biome;coldEnoughToSnow(Lnet/minecraft/core/BlockPos;)Z"))
    private static boolean cold(Biome biome, BlockPos pos, Operation<Boolean> original,
            @Local(argsOnly = true) ServerLevel level) {
        return original.call(biome,IslandClimate.temperaturePosition(level,pos));
    }

    @WrapOperation(method = "setGroupAfloat", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/server/level/ServerLevel;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private static boolean groupClimate(ServerLevel level, Entity entity, Operation<Boolean> original) {
        if (IslandClimate.applies(level)) {
            var pos = design.inherent.skyweavecompat.terrain.LocalWater.waterCell(level,entity.blockPosition());
            if (pos == null || !level.getBiome(pos).value().coldEnoughToSnow(IslandClimate.temperaturePosition(level,pos))) return false;
        }
        return original.call(level,entity);
    }
}
