package design.inherent.skyweavecompat.mixin.ecology;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import design.inherent.skyweavecompat.terrain.IslandHabitat;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Bat.class)
public abstract class BatHabitatMixin {
    @WrapOperation(method="checkBatSpawnRules",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/LevelAccessor;getSeaLevel()I"))
    private static int roof(LevelAccessor level, Operation<Integer> original, @Local(argsOnly=true) BlockPos pos) {
        return IslandHabitat.roof(level,pos,original.call(level));
    }
}
