package design.inherent.skyweavecompat.mixin;

import design.inherent.skyweavecompat.terrain.structure.StructureFamily;
import design.inherent.skyweavecompat.terrain.structure.StructurePlacementScope;
import java.util.List;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.village.VillagePreAnchorPlanner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = VillagePreAnchorPlanner.class, remap = false)
public abstract class StructurePolicyVillageMixin {
    @Inject(method = "exactFirstTargets", at = @At("RETURN"), cancellable = true)
    private static void selectedVillage(CallbackInfoReturnable<List<VillagePreAnchorPlanner.Anchor>> result) {
        var scope = StructurePlacementScope.current();
        if (scope == null || scope.family() != StructureFamily.VILLAGES) return;
        result.setReturnValue(result.getReturnValue().stream().filter(anchor -> scope.owns(anchor.host().preview()))
                .map(anchor -> new VillagePreAnchorPlanner.Anchor(anchor.host(), scope.destination(), anchor.hostRank(),
                        0, VillagePreAnchorPlanner.TargetStage.EXACT_TARGET)).distinct().toList());
    }
}
