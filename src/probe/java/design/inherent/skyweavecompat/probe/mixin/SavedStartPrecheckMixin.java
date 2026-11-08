package design.inherent.skyweavecompat.probe.mixin;

import design.inherent.skyweavecompat.terrain.structure.StructureDistribution;
import design.inherent.skyweavecompat.terrain.structure.StructureFamily;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Reopened-fixture control: saved starts must survive a negative new-terrain precheck. */
@Mixin(StructureDistribution.class)
abstract class SavedStartPrecheckMixin {
    @Inject(method = "possibleNewStart", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void savedOnly(StructureFamily family, int x, int z, CallbackInfoReturnable<Boolean> result) {
        if (Boolean.getBoolean("neighbors.probe.savedStartsOnly")) {
            System.setProperty("neighbors.probe.savedStartsExercised", "true");
            result.setReturnValue(false);
        }
    }
}
