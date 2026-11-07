package design.inherent.skyweavecompat.mixin;

import design.inherent.skyweavecompat.terrain.structure.StructurePlacementScope;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.PlacementRequest;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement.StructureStartVerticalShifter;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.water.WaterPlacementCommitter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = WaterPlacementCommitter.class, remap = false)
public abstract class StructurePolicyWaterPlanningMixin {
    @Inject(method = "commit", at = @At("HEAD"), cancellable = true)
    private void shiftOnly(PlacementRequest request, StructureStart start, int delta, WaterPlacementCommitter.TerrainReservation terrain,
            CallbackInfoReturnable<StructureStart> result) {
        var scope = StructurePlacementScope.current();
        if (scope != null && scope.planning())
            result.setReturnValue(new StructureStartVerticalShifter().shiftY(start, start.getChunkPos(), delta));
    }
}
