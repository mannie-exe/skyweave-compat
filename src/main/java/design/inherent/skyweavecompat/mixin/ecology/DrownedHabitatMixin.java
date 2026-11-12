package design.inherent.skyweavecompat.mixin.ecology;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import design.inherent.skyweavecompat.terrain.LocalWater;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Drowned.class)
public abstract class DrownedHabitatMixin {
    @WrapOperation(method="isDeepEnoughToSpawn",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/LevelAccessor;getSeaLevel()I"))
    private static int water(LevelAccessor level, Operation<Integer> original, @Local(argsOnly=true) BlockPos pos) {
        return LocalWater.spawnSurface(level,pos,original.call(level));
    }
}
