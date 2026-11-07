package design.inherent.skyweavecompat.mixin;

import design.inherent.skyweavecompat.terrain.structure.StructurePlacementScope;
import design.inherent.skyweavecompat.terrain.structure.StructureDistribution;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement.StructureOverlapGuard;
import org.sathrek.sky_archipelago.worldgen.structure.StructureFootprint;
import org.sathrek.sky_archipelago.worldgen.structure.StructurePlacementCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Early 2D queries defer to fitted pieces; final native checks still protect unknown geometry. */
@Mixin(value = StructureOverlapGuard.class, remap = false)
public abstract class StructurePolicySiteOverlapMixin {
    @Inject(method = "wouldOverlap", at = @At("HEAD"), cancellable = true)
    private void fittedVolumesAtCommit(StructureFootprint footprint, StructurePlacementCategory category, long seed,
            CallbackInfoReturnable<Boolean> result) {
        var scope = StructurePlacementScope.current();
        if (scope != null && (scope.planning() || StructureDistribution.hasLayoutPlan(scope.family()))) result.setReturnValue(false);
    }
}
