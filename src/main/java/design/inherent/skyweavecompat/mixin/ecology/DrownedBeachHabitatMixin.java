package design.inherent.skyweavecompat.mixin.ecology;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import design.inherent.skyweavecompat.terrain.LocalWater;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets="net.minecraft.world.entity.monster.Drowned$DrownedGoToBeachGoal")
public abstract class DrownedBeachHabitatMixin {
    @Shadow @Final private Drowned drowned;
    @WrapOperation(method="canUse",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/Level;getSeaLevel()I"))
    private int water(Level level, Operation<Integer> original) {
        return LocalWater.movementSurface(level,drowned.blockPosition(),original.call(level));
    }
}
