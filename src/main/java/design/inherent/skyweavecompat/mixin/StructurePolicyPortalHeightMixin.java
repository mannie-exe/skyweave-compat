package design.inherent.skyweavecompat.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import design.inherent.skyweavecompat.terrain.InteriorField;
import design.inherent.skyweavecompat.terrain.IslandGeometry;
import design.inherent.skyweavecompat.terrain.structure.StructureFamily;
import design.inherent.skyweavecompat.terrain.structure.StructurePlacementScope;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.levelgen.structure.structures.RuinedPortalStructure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Native underground portal search must stop at its island body, not descend through void to the dimension floor. */
@Mixin(RuinedPortalStructure.class)
public abstract class StructurePolicyPortalHeightMixin {
    @ModifyConstant(method = "findSuitableY", constant = @Constant(intValue = 70))
    private static int mountainDatum(int original) {
        var scope = StructurePlacementScope.current();
        if (scope == null || scope.family() != StructureFamily.PORTALS) return original;
        var destination = scope.destination();
        var descriptor = scope.pass().islandField().sampleDominantIslandDescriptor(
                destination.getMiddleBlockX(), destination.getMiddleBlockZ(), scope.pass().settings());
        return descriptor == null || descriptor.archetype().waterBased() ? original
                : IslandGeometry.geologicalFrame(descriptor).toWorld(original);
    }

    @WrapOperation(method = "findSuitableY", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/LevelHeightAccessor;getMinBuildHeight()I"))
    private static int islandFloor(LevelHeightAccessor height, Operation<Integer> original) {
        int floor = original.call(height);
        var scope = StructurePlacementScope.current();
        if (scope == null || scope.family() != StructureFamily.PORTALS) return floor;
        var destination = scope.destination();
        var field = scope.pass().islandField();
        var descriptor = field.sampleDominantIslandDescriptor(destination.getMiddleBlockX(), destination.getMiddleBlockZ(), scope.pass().settings());
        if (descriptor == null || descriptor.archetype().waterBased()) return floor;
        var body = ((InteriorField)(Object)field).skyweave$exteriorOf(descriptor,
                destination.getMiddleBlockX(), destination.getMiddleBlockZ(), scope.pass().settings());
        return body.exists() ? Math.max(floor, body.bottomY()) : floor;
    }
}
