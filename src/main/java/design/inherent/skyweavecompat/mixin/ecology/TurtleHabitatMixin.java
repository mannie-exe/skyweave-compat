package design.inherent.skyweavecompat.mixin.ecology;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import design.inherent.skyweavecompat.terrain.LocalWater;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.animal.Turtle;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Turtle.class)
public abstract class TurtleHabitatMixin {
    @WrapOperation(method="checkTurtleSpawnRules",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/LevelAccessor;getSeaLevel()I"))
    private static int shore(LevelAccessor level, Operation<Integer> original, @Local(argsOnly=true) BlockPos pos) {
        return LocalWater.shoreSurface(level,pos,original.call(level));
    }
}
