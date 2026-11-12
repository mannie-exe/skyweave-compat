package design.inherent.skyweavecompat.terrain.structure;

import design.inherent.skyweavecompat.mixin.VillagePaletteAccess;
import design.inherent.skyweavecompat.mixin.VillageTemplateAccess;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;

/** Native processed feet, retaining raw support intent instead of promoting blocks above authored damage. */
public final class VillageFootprint {
    public enum Status { QUALIFIED, UNSUPPORTED, BUDGET }
    public record Foot(ResourceLocation template,int piece,BlockPos position,BlockPos original,BlockState expected) {
        public Foot {
            Objects.requireNonNull(template);position=position.immutable();original=original.immutable();
            if(piece<0 || piece>=Budget.MAX_PIECES || !foot(Objects.requireNonNull(expected)))
                throw new IllegalArgumentException("Invalid processed structural foot");
        }
    }
    public record Result(Status status,String reason,List<Foot> feet,int rawFeet,int damagedFeet) {
        public Result { feet=List.copyOf(feet); }
        public boolean qualified() { return status==Status.QUALIFIED; }
    }
    /** Shared across a layout, with logical work charged on warm and cold paths alike. */
    public static final class Budget {
        public static final int MAX_PIECES=2048,MAX_BLOCKS=1048576,MAX_FEET=65536;
        private int pieces,blocks,feet;
        boolean piece(int blockCount) {
            if(blockCount<0 || pieces>=MAX_PIECES || blockCount>MAX_BLOCKS-blocks)return false;
            pieces++;blocks+=blockCount;return true;
        }
        boolean feet(int count) { if(count<0 || count>MAX_FEET-feet)return false;feet+=count;return true; }
    }
    private VillageFootprint() {}
    private static Result unavailable(Status status,String reason) { return new Result(status,reason,List.of(),0,0); }
    private static boolean structural(BlockState state) {
        return state.blocksMotion() && state.getFluidState().isEmpty() && !state.is(BlockTags.LEAVES)
                && !state.is(Blocks.JIGSAW) && !state.is(Blocks.STRUCTURE_BLOCK) && !state.is(Blocks.STRUCTURE_VOID);
    }
    /** Raw authored intent shared by early fitting and native material preparation. */
    static List<StructureTemplate.StructureBlockInfo> roots(List<StructureTemplate.StructureBlockInfo> raw,long plane) {
        var lowest=new HashMap<Long,StructureTemplate.StructureBlockInfo>();
        for(var block:raw) {
            if(!structural(block.state()))continue;
            var pos=block.pos();long key=FoundationSupport.key(pos.getX(),pos.getZ());var previous=lowest.get(key);
            if(previous==null || pos.getY()<previous.pos().getY())lowest.put(key,block);
        }
        return lowest.values().stream().filter(block->block.pos().getY()<=plane)
                .sorted(Comparator.comparing(StructureTemplate.StructureBlockInfo::pos)).toList();
    }
    private static boolean foot(BlockState state) {
        if(state.hasBlockEntity() || state.getBlock().hasDynamicShape() || !state.getFluidState().isEmpty())return false;
        if(state.isFaceSturdy(EmptyBlockGetter.INSTANCE,BlockPos.ZERO,Direction.DOWN))return true;
        // Authored lowest roots at the declared floor also include narrow native
        // architectural posts; a full square bottom face is not their geometry.
        if(!(state.getBlock() instanceof FenceBlock || state.getBlock() instanceof WallBlock))return false;
        var shape=state.getBlockSupportShape(EmptyBlockGetter.INSTANCE,BlockPos.ZERO);
        return !shape.isEmpty() && shape.min(Direction.Axis.Y)==0;
    }
    private static final class Trace extends StructureProcessor {
        final Map<StructureTemplate.StructureBlockInfo,StructureTemplate.StructureBlockInfo> origins=new IdentityHashMap<>();
        @Override public StructureTemplate.StructureBlockInfo processBlock(LevelReader level,BlockPos origin,BlockPos reference,
                StructureTemplate.StructureBlockInfo raw,StructureTemplate.StructureBlockInfo current,StructurePlaceSettings settings) {
            origins.put(current,raw);return current;
        }
        @Override protected StructureProcessorType<?> getType() { return StructureProcessorType.NOP; }
    }

    public static Result compile(PoolElementStructurePiece piece,int pieceIndex,BlockPos reference,
            StructureTemplateManager manager,StructureTerrainView terrain,Budget budget,ChunkPos clip) {
        return compileArea(piece,pieceIndex,reference,manager,terrain,budget,clip==null?null:
                new BoundingBox(clip.getMinBlockX(),Integer.MIN_VALUE,clip.getMinBlockZ(),clip.getMaxBlockX(),Integer.MAX_VALUE,clip.getMaxBlockZ()));
    }
    /** A bounded halo uses the same raw intent and processor path as current-chunk feet. */
    public static Result compileArea(PoolElementStructurePiece piece,int pieceIndex,BlockPos reference,
            StructureTemplateManager manager,StructureTerrainView terrain,Budget budget,BoundingBox clip) {
        var element=piece.getElement();
        for(int depth=0;element instanceof VillageDelegate delegate && depth<8;depth++) {
            if(!element.getClass().getName().equals("dev.worldgen.lithostitched.worldgen.poolelement.DelegatingPoolElement"))
                return unavailable(Status.UNSUPPORTED,"delegate");
            element=delegate.skyweave$delegate();
        }
        if(!(element instanceof SinglePoolElement single) || element.getClass()!=SinglePoolElement.class
                && element.getClass()!=net.minecraft.world.level.levelgen.structure.pools.LegacySinglePoolElement.class)
            return unavailable(Status.UNSUPPORTED,"element");
        var access=(VillageTemplateAccess)single;
        var id=access.skyweave$template().left().orElse(null);
        if(id==null)return unavailable(Status.UNSUPPORTED,"inline_template");
        var template=access.skyweave$template(manager);
        var bounds=new BoundingBox(Integer.MIN_VALUE,Integer.MIN_VALUE,Integer.MIN_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE);
        var settings=access.skyweave$settings(piece.getRotation(),bounds,LiquidSettings.APPLY_WATERLOGGING,false);
        var unsupported=VillageProcessors.qualify(settings.getProcessors(),terrain.registries());
        if(unsupported!=null)return unavailable(Status.UNSUPPORTED,unsupported);
        var palettes=((VillagePaletteAccess)template).skyweave$palettes();
        if(palettes.isEmpty())return unavailable(Status.UNSUPPORTED,"no_palette");
        var raw=settings.getRandomPalette(palettes,piece.getPosition()).blocks();
        if(!budget.piece(raw.size()))return unavailable(Status.BUDGET,"template_blocks");
        // Jigsaw assembly derives the piece delta for terrain adaptation. Placement still
        // uses the template's own floor intent, independent of that derived world plane.
        long plane=(long)single.getGroundLevelDelta()-1;
        var candidates=new ArrayList<StructureTemplate.StructureBlockInfo>();
        for(var rawFoot:roots(raw,plane)) {
            var transformed=StructureTemplate.calculateRelativePosition(settings,rawFoot.pos()).offset(piece.getPosition());
            if(clip!=null && !clip.isInside(transformed))continue;
            if(rawFoot.state().hasBlockEntity() || rawFoot.state().getBlock().hasDynamicShape())
                return unavailable(Status.UNSUPPORTED,"dynamic_raw_foot");
            if(foot(rawFoot.state()))candidates.add(rawFoot);
        }
        candidates.sort(Comparator.comparing(StructureTemplate.StructureBlockInfo::pos));
        if(!budget.feet(candidates.size()))return unavailable(Status.BUDGET,"feet");
        var trace=new Trace();settings.addProcessor(trace);
        final List<StructureTemplate.StructureBlockInfo> processed;
        try { processed=StructureTemplate.processBlockInfos(terrain.reader(),piece.getPosition(),reference,settings,candidates,template); }
        catch(StructureTerrainView.Unavailable unknown) { return unavailable(Status.UNSUPPORTED,unknown.getMessage()); }
        if(processed.size()>candidates.size())return unavailable(Status.UNSUPPORTED,"processor_output_expanded");
        var feet=new ArrayList<Foot>();
        for(var block:processed) {
            var original=trace.origins.get(block);
            if(original==null)return unavailable(Status.UNSUPPORTED,"untracked_processor_output");
            var expected=block.state().mirror(settings.getMirror()).rotate(settings.getRotation());
            if(!foot(expected))continue;
            if(!terrain.contains(block.pos()))return unavailable(Status.UNSUPPORTED,"uncaptured_foot");
            var transformed=StructureTemplate.calculateRelativePosition(settings,original.pos()).offset(piece.getPosition());
            if(block.pos().getX()!=transformed.getX() || block.pos().getZ()!=transformed.getZ())
                return unavailable(Status.UNSUPPORTED,"horizontal_processor_movement");
            if(clip!=null && !clip.isInside(block.pos()))return unavailable(Status.UNSUPPORTED,"processor_left_area");
            feet.add(new Foot(id,pieceIndex,block.pos(),original.pos(),expected));
        }
        feet.sort(Comparator.comparing(Foot::position));
        return new Result(Status.QUALIFIED,"native_processed",feet,candidates.size(),candidates.size()-feet.size());
    }
}
