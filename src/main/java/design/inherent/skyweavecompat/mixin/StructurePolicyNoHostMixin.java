package design.inherent.skyweavecompat.mixin;

import design.inherent.skyweavecompat.terrain.structure.StructurePlacementScope;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A miss in a 32-chunk native cache bucket is not evidence against another selected site. */
@Mixin(targets = "org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement.SmallGroundNoHostCache", remap = false)
public abstract class StructurePolicyNoHostMixin {
    @Inject(method = "isCachedMiss", at = @At("HEAD"), cancellable = true)
    private void selectedSiteNeedsOwnFit(CallbackInfoReturnable<Boolean> result) {
        if (StructurePlacementScope.current() != null) result.setReturnValue(false);
    }

    @Inject(method = "recordMiss", at = @At("HEAD"), cancellable = true)
    private void noRegionalMissFromSelectedSite(CallbackInfo result) {
        if (StructurePlacementScope.current() != null) result.cancel();
    }
}
