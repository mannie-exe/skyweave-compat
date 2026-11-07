package design.inherent.skyweavecompat.mixin;

import design.inherent.skyweavecompat.terrain.structure.*;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement.OverlapPolicy3D;
import org.sathrek.sky_archipelago.worldgen.structure.StructurePlacementCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Known raw, terrain-adjusted and reservation bounds share fitted conflicts; foreign geometry stays native. */
@Mixin(value = OverlapPolicy3D.class, remap = false)
public abstract class StructurePolicyOverlapMixin {
    @Inject(method = "conflicts", at = @At("HEAD"), cancellable = true)
    private void fittedPieces(BoundingBox first, StructurePlacementCategory firstCategory,
            BoundingBox second, StructurePlacementCategory secondCategory, CallbackInfoReturnable<Boolean> result) {
        var scope = StructurePlacementScope.current();
        if (scope == null || scope.planning() || first == null || second == null) return;
        var destination = scope.destination();
        var conflict = scope.distribution().layoutConflict(new StructureSelection.Site(destination.x, destination.z), box(first), box(second));
        if (conflict != null) result.setReturnValue(conflict);
    }
    private static StructureVolume.Box box(BoundingBox box) {
        return new StructureVolume.Box(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
    }
}
