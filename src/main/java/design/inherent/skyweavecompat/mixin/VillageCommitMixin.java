package design.inherent.skyweavecompat.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import design.inherent.skyweavecompat.terrain.structure.StructureFamily;
import design.inherent.skyweavecompat.terrain.structure.StructurePlacementScope;
import design.inherent.skyweavecompat.terrain.structure.VillageLayoutRepair;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.PlacementDecision;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.PlacementRequest;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.model.PreAnchorPlacementContext;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.village.GroundVillagePlacementEngine;
import org.spongepowered.asm.mixin.Mixin;

/** Native commitment uses the same original-assembly retention contract as read-only fitting. */
@Mixin(value = GroundVillagePlacementEngine.class, remap = false)
public abstract class VillageCommitMixin {
    @WrapMethod(method = "finalizePreAnchoredVillage")
    private PlacementDecision originalAssembly(PlacementRequest request, PreAnchorPlacementContext context,
            Operation<PlacementDecision> original) {
        var scope = StructurePlacementScope.current();
        if (scope == null || scope.family() != StructureFamily.VILLAGES) return original.call(request, context);
        return VillageLayoutRepair.assembly(request.structureStart(), scope.pass().templateManager(),
                () -> original.call(request, context));
    }
}
