package design.inherent.skyweavecompat.mixin;

import design.inherent.skyweavecompat.terrain.structure.StructurePlacementScope;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.PlacementRequest;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement.StructureStartVerticalShifter;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.underground.UndergroundPlacementCommitter;
import org.sathrek.sky_archipelago.worldgen.structure.StructurePlacementCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Reuse underground search/fit without reserving a target, registering references, or storing a start. */
@Mixin(value = UndergroundPlacementCommitter.class, remap = false)
public abstract class StructurePolicyUndergroundPlanningMixin {
    @Inject(method = "commitCanonical(Lorg/sathrek/sky_archipelago/worldgen/generator/structure/v2/common/PlacementRequest;Lnet/minecraft/world/level/levelgen/structure/StructureStart;Lnet/minecraft/world/level/ChunkPos;ILorg/sathrek/sky_archipelago/worldgen/structure/StructurePlacementCategory;)Lorg/sathrek/sky_archipelago/worldgen/generator/structure/v2/underground/UndergroundPlacementCommitter$CommitResult;",
            at = @At("HEAD"), cancellable = true)
    private void shiftOnly(PlacementRequest request, StructureStart start, ChunkPos destination, int delta,
            StructurePlacementCategory category, CallbackInfoReturnable<UndergroundPlacementCommitter.CommitResult> result) {
        var scope = StructurePlacementScope.current();
        if (scope == null || !scope.planning()) return;
        var shifted = new StructureStartVerticalShifter().shiftY(start, destination, delta);
        result.setReturnValue(new UndergroundPlacementCommitter.CommitResult(true, "layout_only", shifted, null));
    }
}
