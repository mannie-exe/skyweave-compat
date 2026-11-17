package design.inherent.skyweavecompat.mixin.encounters;

import com.faboslav.variantsandventures.common.entity.mob.MurkEntity;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import design.inherent.skyweavecompat.encounters.MurkWater;
import design.inherent.skyweavecompat.terrain.LocalWater;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets="com.faboslav.variantsandventures.common.entity.ai.goal.TargetAboveWaterGoal")
public abstract class MurkGoalMixin {
    @Shadow @Final private MurkEntity murk;
    @Unique private int skyweave$waterDatum(){
        return LocalWater.applies(murk.level())?MurkWater.datum(murk.level(),murk.blockPosition()).orElse(Integer.MIN_VALUE):Integer.MAX_VALUE;
    }
    @Inject(method="canUse",at=@At("HEAD"),cancellable=true,require=1)
    private void ready(CallbackInfoReturnable<Boolean> result,@Share("waterDatum") LocalIntRef datum){
        datum.set(skyweave$waterDatum());if(datum.get()==Integer.MIN_VALUE)result.setReturnValue(false);
    }
    @Inject(method="tick",at=@At("HEAD"),cancellable=true,require=1)
    private void readyTick(CallbackInfo result,@Share("waterDatum") LocalIntRef datum){
        datum.set(skyweave$waterDatum());if(datum.get()==Integer.MIN_VALUE)result.cancel();
    }
    @WrapOperation(method={"canUse","tick"},at=@At(value="FIELD",target="Lcom/faboslav/variantsandventures/common/entity/ai/goal/TargetAboveWaterGoal;minY:I"),require=3,expect=3)
    private int water(@Coerce Object goal,Operation<Integer> original,@Share("waterDatum") LocalIntRef datum){
        return datum.get()==Integer.MAX_VALUE?original.call(goal):datum.get();
    }
}
