package design.inherent.skyweavecompat.mixin.encounters;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import design.inherent.skyweavecompat.encounters.MurkWater;
import design.inherent.skyweavecompat.terrain.LocalWater;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets="com.faboslav.variantsandventures.common.entity.mob.MurkEntity")
public abstract class MurkDepthMixin {
    @WrapOperation(method="isValidSpawnDepth",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/LevelAccessor;getSeaLevel()I"),require=1)
    private static int water(LevelAccessor level,Operation<Integer> original,@Local(argsOnly=true) BlockPos pos){
        if(!LocalWater.applies(level))return original.call(level);
        return MurkWater.datum(level,pos).orElse(Integer.MIN_VALUE+16);
    }
}
