package design.inherent.skyweavecompat.mixin.water;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import design.inherent.skyweavecompat.climate.IslandClimate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Preserve native aging and drift; only its warm/cold classification changes. */
@Mixin(targets = "com.AdamNew.dynamicwaters.entity.IceFloe", remap = false)
public abstract class FloeLifetimeClimateMixin extends Entity {
    protected FloeLifetimeClimateMixin(EntityType<?> type, Level level) { super(type,level); }
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/biome/Biome;coldEnoughToSnow(Lnet/minecraft/core/BlockPos;)Z"))
    private boolean cold(Biome biome, BlockPos pos, Operation<Boolean> original) {
        if (IslandClimate.applies(level())) {
            var water=design.inherent.skyweavecompat.terrain.LocalWater.waterCell(level(),pos);
            if(water!=null) return original.call(level().getBiome(water).value(),IslandClimate.temperaturePosition(level(),water));
        }
        return original.call(biome,IslandClimate.temperaturePosition(level(),pos));
    }
}
