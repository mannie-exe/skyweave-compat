package design.inherent.skyweavecompat.mixin;

import design.inherent.skyweavecompat.terrain.structure.StructureWorldScope;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement.PlacementCommitCoordinator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = PlacementCommitCoordinator.class, remap = false)
public abstract class StructurePolicyPendingOwnerMixin {
    @ModifyArg(method = "reserveOrConflict(Lnet/minecraft/world/level/levelgen/structure/StructureStart;Lnet/minecraft/world/level/levelgen/structure/BoundingBox;Lorg/sathrek/sky_archipelago/worldgen/structure/StructurePlacementCategory;Lorg/sathrek/sky_archipelago/worldgen/generator/structure/v2/common/placement/ReservationContext;Lnet/minecraft/world/level/ChunkPos;Z)Lorg/sathrek/sky_archipelago/worldgen/generator/structure/v2/common/placement/PlacementCommitCoordinator$Decision;", at = @At(value = "INVOKE",
            target = "Lorg/sathrek/sky_archipelago/worldgen/generator/structure/v2/common/placement/PendingStructurePlacement;<init>(Ljava/lang/Runnable;)V"), index = 0)
    private Runnable ownerCallback(Runnable revoke) {
        return StructureWorldScope.bind(revoke);
    }
}
