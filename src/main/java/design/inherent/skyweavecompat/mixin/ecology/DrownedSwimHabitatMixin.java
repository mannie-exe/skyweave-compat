package design.inherent.skyweavecompat.mixin.ecology;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import design.inherent.skyweavecompat.terrain.LocalWater;
import net.minecraft.world.entity.monster.Drowned;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets="net.minecraft.world.entity.monster.Drowned$DrownedSwimUpGoal")
public abstract class DrownedSwimHabitatMixin {
    @Shadow @Final private Drowned drowned;
    @WrapOperation(method={"canUse","tick"},at=@At(value="FIELD",target="Lnet/minecraft/world/entity/monster/Drowned$DrownedSwimUpGoal;seaLevel:I"),require=3)
    private int water(@org.spongepowered.asm.mixin.injection.Coerce Object goal, Operation<Integer> original) {
        return LocalWater.movementSurface(drowned.level(),drowned.blockPosition(),original.call(goal));
    }
}
