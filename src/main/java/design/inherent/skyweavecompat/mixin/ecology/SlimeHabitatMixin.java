package design.inherent.skyweavecompat.mixin.ecology;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import design.inherent.skyweavecompat.terrain.IslandHabitat;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Slime.class)
public abstract class SlimeHabitatMixin {
    @WrapOperation(method="checkSlimeSpawnRules",at=@At(value="INVOKE",target="Lnet/minecraft/core/BlockPos;getY()I",ordinal=0))
    private static int surfaceMinimum(BlockPos pos, Operation<Integer> original, @Local(argsOnly=true) LevelAccessor level) {
        return IslandHabitat.surfaceAltitude(level,pos);
    }
    @WrapOperation(method="checkSlimeSpawnRules",at=@At(value="INVOKE",target="Lnet/minecraft/core/BlockPos;getY()I",ordinal=1))
    private static int surfaceMaximum(BlockPos pos, Operation<Integer> original, @Local(argsOnly=true) LevelAccessor level) {
        return IslandHabitat.surfaceAltitude(level,pos);
    }
    @WrapOperation(method="checkSlimeSpawnRules",at=@At(value="INVOKE",target="Lnet/minecraft/core/BlockPos;getY()I",ordinal=2))
    private static int underground(BlockPos pos, Operation<Integer> original, @Local(argsOnly=true) LevelAccessor level) {
        return IslandHabitat.undergroundAltitude(level,pos);
    }
}
