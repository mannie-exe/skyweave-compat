package design.inherent.skyweavecompat.probe.mixin;

import com.AdamNew.dynamicwaters.HydrologyManager;
import design.inherent.skyweavecompat.probe.HydrologyProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Observe the actual native repair reads, rather than invoking the adapter as a substitute. */
@Pseudo
@Mixin(targets = "design.inherent.skyweavecompat.water.WaterRepairScope", remap = false)
abstract class WaterRepairWitnessMixin {
    private static final boolean WATER = !System.getProperty("neighbors.probe.waterWitnesses", "").isEmpty();

    @Inject(method = "reference", at = @At("RETURN"))
    private static void observe(HydrologyManager.RiverCarving cut, int original, CallbackInfoReturnable<Integer> callback) {
        if (WATER) HydrologyProbe.recordRepairReference(cut, original, callback.getReturnValue());
    }
}
