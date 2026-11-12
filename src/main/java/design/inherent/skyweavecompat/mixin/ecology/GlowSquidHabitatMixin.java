package design.inherent.skyweavecompat.mixin.ecology;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import design.inherent.skyweavecompat.terrain.IslandHabitat;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.GlowSquid;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GlowSquid.class)
public abstract class GlowSquidHabitatMixin {
    @WrapOperation(method="checkGlowSquidSpawnRules",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/ServerLevelAccessor;getSeaLevel()I"))
    private static int water(ServerLevelAccessor level, Operation<Integer> original, @Local(argsOnly=true) BlockPos pos) {
        return IslandHabitat.roof(level,pos,original.call(level));
    }
}
