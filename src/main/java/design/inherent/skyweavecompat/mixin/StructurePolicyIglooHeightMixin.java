package design.inherent.skyweavecompat.mixin;

import design.inherent.skyweavecompat.terrain.structure.StructureFamily;
import design.inherent.skyweavecompat.terrain.structure.StructurePlacementScope;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.structures.IglooStructure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Draft the entire igloo at its terrain anchor before upstream checks its grounding. */
@Mixin(IglooStructure.class)
public abstract class StructurePolicyIglooHeightMixin {
    @ModifyConstant(method = "generatePieces", constant = @Constant(intValue = 90))
    private int islandAnchor(int original, StructurePiecesBuilder pieces, Structure.GenerationContext context) {
        var scope = StructurePlacementScope.current();
        if (scope == null || scope.family() != StructureFamily.IGLOOS) return original;
        return context.chunkGenerator().getFirstOccupiedHeight(context.chunkPos().getMiddleBlockX(), context.chunkPos().getMiddleBlockZ(),
                Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
    }
}
