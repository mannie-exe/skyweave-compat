package design.inherent.skyweavecompat.mixin;

import design.inherent.skyweavecompat.terrain.structure.StructurePlacementScope;
import design.inherent.skyweavecompat.terrain.structure.StructureDistribution;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.registry.UndergroundFinalTargetRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = UndergroundFinalTargetRegistry.class, remap = false)
public abstract class StructurePolicyUndergroundClaimsMixin {
    @Inject(method = "isClaimed", at = @At("HEAD"), cancellable = true)
    private static void independentLayout(CallbackInfoReturnable<Boolean> result) {
        var scope = StructurePlacementScope.current();
        if (scope != null && StructureDistribution.hasLayoutPlan(scope.family())) result.setReturnValue(false);
    }

    @Inject(method = "claim", at = @At("HEAD"), cancellable = true)
    private static void noPlanningClaim(CallbackInfoReturnable<Boolean> result) {
        var scope = StructurePlacementScope.current();
        if (scope != null && StructureDistribution.hasLayoutPlan(scope.family())) result.setReturnValue(true);
    }

    @Inject(method = "release", at = @At("HEAD"), cancellable = true)
    private static void preserveForeignClaims(CallbackInfo result) {
        var scope = StructurePlacementScope.current();
        if (scope != null && StructureDistribution.hasLayoutPlan(scope.family())) result.cancel();
    }
}
