package design.inherent.skyweavecompat.mixin;

import design.inherent.skyweavecompat.terrain.structure.StructureWorldScope;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Preserve native duplicate checks with a generator-owned key set instead of one process-global set. */
@Mixin(targets = "org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement.PlacementCommitCoordinator$PhysicalStructureClaimRegistry", remap = false)
public abstract class StructurePolicyPhysicalClaimsMixin {
    @Inject(method = "tryClaim", at = @At("HEAD"), cancellable = true)
    private void fittedSlot(@Coerce Object key, CallbackInfoReturnable<Object> result) {
        var world = StructureWorldScope.current();
        if (world != null) result.setReturnValue(world.claimPhysical(key));
    }

    @Inject(method = "release", at = @At("HEAD"), cancellable = true)
    private void preserveUnownedClaim(@Coerce Object key, CallbackInfo result) {
        var world = StructureWorldScope.current();
        if (world != null) { world.releasePhysical(key); result.cancel(); }
    }
}
