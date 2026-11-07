package design.inherent.skyweavecompat.mixin;

import design.inherent.skyweavecompat.terrain.structure.StructurePlacementScope;
import design.inherent.skyweavecompat.terrain.structure.StructureDistribution;
import java.util.function.Supplier;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.registry.IslandAwareStructureAnchorCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Scoped host/site rules are not represented in the upstream cache key. Do not reuse unscoped results. */
@Mixin(value = IslandAwareStructureAnchorCache.class, remap = false)
public abstract class StructurePolicyAnchorCacheMixin {
    @Inject(method = "getOrResolveCandidates", at = @At("HEAD"), cancellable = true)
    private static void selectedCandidates(IslandAwareStructureAnchorCache.Query query,
            Supplier<IslandAwareStructureAnchorCache.CandidateResolution> resolver,
            CallbackInfoReturnable<IslandAwareStructureAnchorCache.CandidateResult> result) {
        if (StructurePlacementScope.current() == null) return;
        var resolved = resolver.get();
        result.setReturnValue(new IslandAwareStructureAnchorCache.CandidateResult(resolved.candidates(),
                IslandAwareStructureAnchorCache.CacheDisposition.MISS, resolved.details(), resolved.diagnostics()));
    }

    @Inject(method = "isClaimed", at = @At("HEAD"), cancellable = true)
    private static void plannedVolumesOwnExclusion(CallbackInfoReturnable<Boolean> result) {
        if (fitted()) result.setReturnValue(false);
    }

    @Inject(method = "claim", at = @At("HEAD"), cancellable = true)
    private static void noSharedAnchorClaim(CallbackInfoReturnable<Boolean> result) {
        if (fitted()) result.setReturnValue(true);
    }

    private static boolean fitted() {
        var scope = StructurePlacementScope.current();
        return scope != null && StructureDistribution.hasLayoutPlan(scope.family());
    }
}
