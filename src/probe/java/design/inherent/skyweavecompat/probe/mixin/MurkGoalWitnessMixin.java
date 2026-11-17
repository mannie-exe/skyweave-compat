package design.inherent.skyweavecompat.probe.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import design.inherent.skyweavecompat.probe.MurkEncounterProbe;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Observes the actual target given to native random-position search without changing it. */
@Pseudo
@Mixin(targets="com.faboslav.variantsandventures.common.entity.ai.goal.TargetAboveWaterGoal")
public abstract class MurkGoalWitnessMixin {
    @WrapOperation(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/ai/util/DefaultRandomPos;getPosTowards(Lnet/minecraft/world/entity/PathfinderMob;IILnet/minecraft/world/phys/Vec3;D)Lnet/minecraft/world/phys/Vec3;"),require=1)
    private Vec3 target(PathfinderMob mob,int horizontal,int vertical,Vec3 target,double angle,Operation<Vec3> original) {
        if(Boolean.getBoolean("neighbors.probe.encounters"))MurkEncounterProbe.goalTargets.add(target.y);
        return original.call(mob,horizontal,vertical,target,angle);
    }
}
