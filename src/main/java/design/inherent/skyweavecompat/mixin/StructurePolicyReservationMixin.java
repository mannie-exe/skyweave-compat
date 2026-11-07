package design.inherent.skyweavecompat.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import design.inherent.skyweavecompat.terrain.structure.StructureWorldScope;
import org.objectweb.asm.Opcodes;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement.FcfsArbiter;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement.StructurePlacementReservationRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Keep the native arbiter, padding, takeover and bounded store; isolate its lifetime by generator. */
@Mixin(value = StructurePlacementReservationRegistry.class, remap = false)
public abstract class StructurePolicyReservationMixin {
    @WrapOperation(method = {"wouldOverlap",
            "tryReserve(Lorg/sathrek/sky_archipelago/worldgen/structure/StructureFootprint;Lorg/sathrek/sky_archipelago/worldgen/structure/StructurePlacementCategory;Lorg/sathrek/sky_archipelago/worldgen/generator/structure/v2/common/placement/ReservationContext;)Z",
            "tryReserve(Lnet/minecraft/world/level/levelgen/structure/BoundingBox;Lorg/sathrek/sky_archipelago/worldgen/structure/StructurePlacementCategory;Lorg/sathrek/sky_archipelago/worldgen/generator/structure/v2/common/placement/ReservationContext;)Lorg/sathrek/sky_archipelago/worldgen/generator/structure/v2/common/placement/FcfsArbiter$Decision;",
            "tryReserveTiered", "tryReservePending"}, at = @At(value = "FIELD",
            target = "Lorg/sathrek/sky_archipelago/worldgen/generator/structure/v2/common/placement/StructurePlacementReservationRegistry;ARBITER:Lorg/sathrek/sky_archipelago/worldgen/generator/structure/v2/common/placement/FcfsArbiter;",
            opcode = Opcodes.GETSTATIC), require = 5)
    private static FcfsArbiter worldArbiter(Operation<FcfsArbiter> original) {
        var world = StructureWorldScope.current();
        return world == null ? original.call() : world.reservations();
    }
}
