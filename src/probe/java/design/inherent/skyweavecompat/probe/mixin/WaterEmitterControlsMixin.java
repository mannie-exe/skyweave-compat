package design.inherent.skyweavecompat.probe.mixin;

import design.inherent.skyweavecompat.probe.WaterEmissionControls;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="com.AdamNew.dynamicwaters.terrain.BasinTile$Builder",remap=false)
public abstract class WaterEmitterControlsMixin {
    @Inject(method="emit",at=@At(value="INVOKE",target="Lcom/AdamNew/dynamicwaters/terrain/BasinTile;reverse([D)V",ordinal=0))
    private void skyweave$emitterControls(int[] cells,int from,int to,int owner,boolean source,boolean lake,boolean confluence,
            boolean mouth,boolean hitMain,int inlet,@Coerce Object main,double offset,CallbackInfo callback) {
        if(Boolean.getBoolean("neighbors.probe.waterEmitters"))WaterEmissionControls.observe(this,cells,from,to,owner,source,lake,confluence,mouth,hitMain,inlet,main,offset);
    }
}
