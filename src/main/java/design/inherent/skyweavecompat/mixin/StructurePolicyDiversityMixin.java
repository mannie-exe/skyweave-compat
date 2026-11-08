package design.inherent.skyweavecompat.mixin;

import design.inherent.skyweavecompat.terrain.structure.StructureFamily;
import design.inherent.skyweavecompat.terrain.structure.StructurePlacementScope;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.model.HostIslandKey;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.model.StructurePlacementPolicy;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement.StructureDiversityGate;
import org.sathrek.sky_archipelago.worldgen.structure.StructurePlacementCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Family allowances replace the expiring native exact-ID counters for owned attempts. */
@Mixin(value = StructureDiversityGate.class, remap = false)
public abstract class StructurePolicyDiversityMixin {
    @Inject(method = "evaluateOrigin", at = @At("HEAD"), cancellable = true)
    private void familyOrigin(ResourceLocation id, ChunkPos origin, StructurePlacementPolicy policy,
            CallbackInfoReturnable<StructureDiversityGate.OriginResult> result) {
        if (owned(id)) result.setReturnValue(new StructureDiversityGate.OriginResult(true, "cluster_family_plan", false, 0));
    }

    @Inject(method = "evaluateHost", at = @At("HEAD"), cancellable = true)
    private void familyHost(ResourceLocation id, StructurePlacementCategory category, HostIslandKey host, StructurePlacementPolicy policy,
            CallbackInfoReturnable<StructureDiversityGate.HostResult> result) {
        if (owned(id)) result.setReturnValue(new StructureDiversityGate.HostResult(true, "cluster_family_plan", 0,
                policy.maxPerHostIsland(), 0, policy.maxSameStructurePerHostIsland()));
    }

    private static boolean owned(ResourceLocation id) {
        var scope = StructurePlacementScope.current();
        return scope != null && id != null && scope.distribution().family(id) == scope.family();
    }
}
