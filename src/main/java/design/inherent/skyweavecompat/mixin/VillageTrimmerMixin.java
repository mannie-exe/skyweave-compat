package design.inherent.skyweavecompat.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import design.inherent.skyweavecompat.terrain.structure.StructureFamily;
import design.inherent.skyweavecompat.terrain.structure.StructurePlacementScope;
import design.inherent.skyweavecompat.terrain.structure.VillageRoadEnds;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.model.HostIsland;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.village.VillageLayoutTrimmer;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value=VillageLayoutTrimmer.class,remap=false)
public abstract class VillageTrimmerMixin {
    @WrapMethod(method="trim")
    private VillageLayoutTrimmer.TrimResult terminate(StructureStart start,ChunkPos source,HostIsland host,
            Operation<VillageLayoutTrimmer.TrimResult> original) {
        var result=original.call(start,source,host);
        var scope=StructurePlacementScope.current();
        if(scope==null || scope.family()!=StructureFamily.VILLAGES || !result.accepted())return result;
        var pieces=VillageRoadEnds.close(result.structureStart().getPieces(),scope.pass().islandField(),scope.pass().settings());
        int added=pieces.size()-result.structureStart().getPieces().size();
        if(added==0)return result;
        var replacement=new StructureStart(start.getStructure(),source,start.getReferences(),new PiecesContainer(pieces));
        var bounds=net.minecraft.world.level.levelgen.structure.BoundingBox.encapsulatingBoxes(pieces.stream().map(piece->piece.getBoundingBox()).toList()).orElseThrow();
        return new VillageLayoutTrimmer.TrimResult(true,result.stage(),result.tier(),replacement,bounds,pieces.size(),result.removedPieces(),
                result.originalPieces()+added,result.candidatePieces()+added,result.envelopeDropCount(),result.boundsDropCount(),
                result.pieceFitDropCount(),result.finalBoundsDropCount(),result.boundsDropCounts());
    }
}
