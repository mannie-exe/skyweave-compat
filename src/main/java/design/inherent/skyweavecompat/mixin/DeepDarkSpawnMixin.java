package design.inherent.skyweavecompat.mixin;

import design.inherent.skyweavecompat.terrain.DeepDarkHabitat;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SpawnPlacements.class)
public abstract class DeepDarkSpawnMixin {
    @Inject(method="checkSpawnRules",at=@At("RETURN"),cancellable=true)
    private static void skyweave$passive(EntityType<?> type, ServerLevelAccessor level, MobSpawnType reason,
            BlockPos position, RandomSource random, CallbackInfoReturnable<Boolean> result) {
        if (!result.getReturnValue() || reason != MobSpawnType.NATURAL && reason != MobSpawnType.CHUNK_GENERATION) return;
        boolean passive = switch(type.getCategory()) {
            case CREATURE, AMBIENT, AXOLOTLS, UNDERGROUND_WATER_CREATURE, WATER_CREATURE, WATER_AMBIENT -> true;
            default -> false;
        };
        if (passive && DeepDarkHabitat.contains(level,position)) result.setReturnValue(false);
    }
}
