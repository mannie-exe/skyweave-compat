package design.inherent.skyweavecompat.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import design.inherent.skyweavecompat.terrain.structure.StructureFamily;
import design.inherent.skyweavecompat.terrain.structure.StructurePlacementScope;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import org.sathrek.sky_archipelago.worldgen.structure.PieceAwareSupportPlaneResolver;
import org.sathrek.sky_archipelago.worldgen.structure.ResolvedStructureSupportPlane;
import org.spongepowered.asm.mixin.Mixin;

/** A buried laboratory is not the igloo's surface contact plane. */
@Mixin(value = PieceAwareSupportPlaneResolver.class, remap = false)
public abstract class StructurePolicyIglooSupportMixin {
    @WrapMethod(method = "resolve")
    private Optional<ResolvedStructureSupportPlane> topSupport(ResourceLocation id, StructureStart start, double inset,
            Operation<Optional<ResolvedStructureSupportPlane>> original) {
        var scope = StructurePlacementScope.current();
        if (scope == null || scope.family() != StructureFamily.IGLOOS || !start.isValid()) return original.call(id, start, inset);
        // Vanilla adds the laboratory and ladder below the top; the top has the highest piece base.
        var top = start.getPieces().stream().max(Comparator.comparingInt(piece -> piece.getBoundingBox().minY())).orElse(null);
        if (top == null) return original.call(id, start, inset);
        var surface = new StructureStart(start.getStructure(), start.getChunkPos(), start.getReferences(), new PiecesContainer(List.of(top)));
        return original.call(id, surface, inset);
    }
}
