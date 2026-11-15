package design.inherent.skyweavecompat.terrain.structure;

import design.inherent.skyweavecompat.mixin.VillagePaletteAccess;
import design.inherent.skyweavecompat.mixin.VillageTemplateAccess;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.*;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;

/** Local rigid decoration may not excavate an inhabited piece's authored immediate support. */
final class VillageDecorationConflicts {
    static final class Limit extends RuntimeException { Limit(String reason) { super(reason); } }
    record Conflict(StructurePiece decoration, StructurePiece building, BlockPos cut, BlockPos foot) {}
    private record Bearing(StructurePiece piece, FoundationSupport.Foot foot) {}
    private VillageDecorationConflicts() {}

    static List<Conflict> inspect(List<StructurePiece> pieces, StructureTemplateManager templates, int maximumGap) {
        var columns=new HashMap<Long,List<Bearing>>();int feet=0,blocks=0,comparisons=0;
        var nodes=pieces.stream().map(piece->VillageLayoutRepair.node(piece,templates)).toList();
        for(var node:nodes) {
            if(node.projected() || node.refusal()!=null || node.role()!=VillageLayoutRepair.Role.BUILDING
                    && node.role()!=VillageLayoutRepair.Role.CENTER && node.role()!=VillageLayoutRepair.Role.FARM)continue;
            for(var foot:node.feet()) {
                if(++feet>VillageFootprint.Budget.MAX_FEET)throw new Limit("decoration_support_foot_budget");
                columns.computeIfAbsent(FoundationSupport.key(foot.x(),foot.z()),key->new ArrayList<>()).add(new Bearing(node.piece(),foot));
            }
        }
        var conflicts=new ArrayList<Conflict>();
        for(var node:nodes) {
            if(node.role()!=VillageLayoutRepair.Role.DECORATION || node.projected()
                    || node.piece().getClass()!=PoolElementStructurePiece.class)continue;
            var piece=(PoolElementStructurePiece)node.piece();var element=piece.getElement();
            for(int depth=0;element instanceof VillageDelegate delegate && depth<8;depth++)element=delegate.skyweave$delegate();
            if(element.getClass()!=SinglePoolElement.class && element.getClass()!=LegacySinglePoolElement.class)continue;
            var single=(SinglePoolElement)element;var access=(VillageTemplateAccess)single;
            var settings=access.skyweave$settings(piece.getRotation(),piece.getBoundingBox(),LiquidSettings.APPLY_WATERLOGGING,false);
            // These leaves cannot turn ordinary blocks into cuts or move their positions.
            if(settings.getProcessors().stream().anyMatch(processor->processor.getClass()!=BlockIgnoreProcessor.class
                    && processor.getClass()!=JigsawReplacementProcessor.class && processor.getClass()!=NopProcessor.class))continue;
            var palettes=((VillagePaletteAccess)access.skyweave$template(templates)).skyweave$palettes();
            if(palettes.isEmpty())continue;
            var palette=settings.getRandomPalette(palettes,piece.getPosition());
            for(var block:palette.blocks()) {
                if(++blocks>VillageFootprint.Budget.MAX_BLOCKS)throw new Limit("decoration_cut_block_budget");
                boolean air=block.state().is(Blocks.AIR) && !(element instanceof LegacySinglePoolElement);
                if(!air && !block.state().is(Blocks.WATER) && !block.state().is(Blocks.LAVA))continue;
                var at=StructureTemplate.calculateRelativePosition(settings,block.pos()).offset(piece.getPosition());
                if(!piece.getBoundingBox().isInside(at))continue;
                boolean found=false;
                for(var bearing:columns.getOrDefault(FoundationSupport.key(at.getX(),at.getZ()),List.of())) {
                    if(++comparisons>VillageFootprint.Budget.MAX_BLOCKS)throw new Limit("decoration_support_comparison_budget");
                    var foot=bearing.foot();
                    if(at.getY()<=foot.y() && (long)at.getY()>=foot.y()-maximumGap-1L) {
                        conflicts.add(new Conflict(piece,bearing.piece(),at,new BlockPos(foot.x(),foot.y(),foot.z())));
                        found=true;break;
                    }
                }
                if(found)break;
            }
        }
        return List.copyOf(conflicts);
    }
}
