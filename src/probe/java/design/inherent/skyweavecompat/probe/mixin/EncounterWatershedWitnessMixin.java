package design.inherent.skyweavecompat.probe.mixin;

import design.inherent.skyweavecompat.probe.MurkEncounterProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Constructor observation only; never changes the optional native planner. */
@Pseudo
@Mixin(targets="com.AdamNew.dynamicwaters.terrain.BasinTile$Builder")
public abstract class EncounterWatershedWitnessMixin {
    @Inject(method="<init>",at=@At("RETURN"),require=1)
    private void constructed(CallbackInfo ci) {
        if(Boolean.getBoolean("neighbors.probe.encounters")&&MurkEncounterProbe.measuringWater)MurkEncounterProbe.planningConstructors++;
    }
}
