package design.inherent.skyweavecompat.terrain.structure;

import design.inherent.skyweavecompat.terrain.*;
import design.inherent.skyweavecompat.mixin.StructureBeardifierAccess;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pools.*;
import org.sathrek.sky_archipelago.worldgen.generator.field.model.IslandDescriptor;

/** Positive native beard density, bounded by qualified feet and a single natural roof contributor. */
public final class VillageGrading {
    public static final int HALO=12,MAX_CELLS=8192;
    public enum Status { READY, UNKNOWN, BUDGET }
    public record Cell(BlockPos position,BlockState original,BlockState target) {
        public Cell {
            position=position.immutable();Objects.requireNonNull(original);Objects.requireNonNull(target);
            if(!original.getFluidState().isEmpty() || original.hasBlockEntity()
                    || !original.isAir() && !NaturalMaterials.skin(original) || !NaturalMaterials.skin(target) || !sturdy(target))
                throw new IllegalArgumentException("Invalid natural grading cell");
        }
    }
    public record Part(PoolElementStructurePiece piece,BoundingBox physical,TerrainAdjustment adjustment,List<VillageFoundation.Binding> feet) {
        public Part {
            Objects.requireNonNull(piece);Objects.requireNonNull(physical);Objects.requireNonNull(adjustment);feet=List.copyOf(feet);
            physical=new BoundingBox(physical.minX(),physical.minY(),physical.minZ(),physical.maxX(),physical.maxY(),physical.maxZ());
        }
    }
    public record Result(Status status,List<Cell> cells,int columns) {
        public Result {
            cells=List.copyOf(cells);
            if(cells.size()>MAX_CELLS || status!=Status.READY && !cells.isEmpty()
                    || cells.stream().map(Cell::position).distinct().count()!=cells.size())
                throw new IllegalArgumentException("Invalid grading result");
        }
    }
    private record Kernel(IslandDescriptor owner,Beardifier density,List<Part> parts) {}
    private VillageGrading() {}
    private static boolean sturdy(BlockState state) {
        return !state.getBlock().hasDynamicShape() && !state.hasBlockEntity() && state.getFluidState().isEmpty()
                && state.isFaceSturdy(EmptyBlockGetter.INSTANCE,BlockPos.ZERO,Direction.UP);
    }
    public static Result plan(ChunkPos chunk,StructureTerrainView natural,List<Part> parts,
            BiFunction<Integer,Integer,IslandContext> contexts,Predicate<BlockPos> protectedCell,int maximumRise) {
        if(maximumRise<0 || maximumRise>8 || parts.size()>VillageFootprint.Budget.MAX_PIECES)
            throw new IllegalArgumentException("Invalid village grading bounds");
        var grouped=new LinkedHashMap<IslandDescriptor,List<Part>>();
        for(var part:parts) {
            if(part.adjustment()==TerrainAdjustment.NONE || part.piece().getElement().getProjection()!=StructureTemplatePool.Projection.RIGID)continue;
            if(part.piece() instanceof net.neoforged.neoforge.common.world.PieceBeardifierModifier)
                return new Result(Status.UNKNOWN,List.of(),0);
            var owners=part.feet().stream().filter(VillageFoundation.Binding::supported).map(VillageFoundation.Binding::owner)
                    .filter(Objects::nonNull).map(IslandContext.Body::island).distinct().toList();
            // A crossing piece has no single body's authority; its individual feet remain separate.
            if(owners.size()==1)grouped.computeIfAbsent(owners.getFirst(),ignored->new ArrayList<>()).add(part);
        }
        var kernels=new ArrayList<Kernel>();
        for(var entry:grouped.entrySet()) {
            var rigid=new ObjectArrayList<Beardifier.Rigid>();var junctions=new ObjectArrayList<JigsawJunction>();
            for(var part:entry.getValue()) {
                var piece=part.piece();var box=part.physical();
                rigid.add(new Beardifier.Rigid(new BoundingBox(box.minX(),box.minY(),box.minZ(),box.maxX(),box.maxY(),box.maxZ()),
                        part.adjustment(),piece.getGroundLevelDelta()));
                for(var junction:piece.getJunctions())if(junction.getSourceX()>chunk.getMinBlockX()-HALO
                        && junction.getSourceX()<chunk.getMaxBlockX()+HALO && junction.getSourceZ()>chunk.getMinBlockZ()-HALO
                        && junction.getSourceZ()<chunk.getMaxBlockZ()+HALO)junctions.add(junction);
            }
            kernels.add(new Kernel(entry.getKey(),new Beardifier(rigid.iterator(),junctions.iterator()),List.copyOf(entry.getValue())));
        }
        var cells=new TreeMap<BlockPos,Cell>();int columns=0;
        try {
            for(int x=chunk.getMinBlockX();x<=chunk.getMaxBlockX();x++)for(int z=chunk.getMinBlockZ();z<=chunk.getMaxBlockZ();z++) {
                var context=contexts.apply(x,z);if(context==null)return new Result(Status.UNKNOWN,List.of(),columns);
                for(var kernel:kernels) {
                    var relevant=new ArrayList<Part>();
                    for(var part:kernel.parts())if(part.physical().intersects(x-HALO,z-HALO,x+HALO,z+HALO))relevant.add(part);
                    int cap=relevant.stream().flatMap(part->part.feet().stream()).filter(VillageFoundation.Binding::supported)
                            .mapToInt(binding->binding.foot().position().getY()-1).min().orElse(Integer.MIN_VALUE);
                    if(cap==Integer.MIN_VALUE)continue;
                    var body=context.bodies().stream().filter(value->value.island().equals(kernel.owner())).findFirst().orElse(null);
                    if(body==null)continue;
                    for(int sourceY=cap;sourceY>=cap-maximumRise;sourceY--) {
                        var source=new BlockPos(x,sourceY,z);var sourceState=natural.state(source);
                        var owner=context.solidOwner(sourceY);
                        if(!sturdy(sourceState) || !NaturalMaterials.skin(sourceState) || owner==null || !owner.island().equals(kernel.owner())
                                || context.naturalAt(body,sourceY+1)!=IslandContext.Occupancy.OUTSIDE || protectedCell.test(source))continue;
                        int top=sourceY;
                        int nativeTop=StructureBeardifierAccess.skyweave$adjustedTop(kernel.density(),x,z,
                                new org.sathrek.sky_archipelago.worldgen.generator.field.TerrainColumn(body.exterior().bottomY(),sourceY));
                        for(int y=sourceY+1;y<=Math.min(nativeTop,Math.min(cap,sourceY+maximumRise));y++) {
                            var at=new BlockPos(x,y,z);
                            if(!natural.state(at).isAir() || protectedCell.test(at)
                                    || context.contributions(y).stream().anyMatch(c->c.occupancy()!=IslandContext.Occupancy.OUTSIDE))break;
                            top=y;
                        }
                        if(top==sourceY)break;
                        columns++;var palette=new ArrayList<BlockState>();palette.add(sourceState);
                        for(int d=1;d<=2;d++) {
                            var below=source.below(d);var value=natural.state(below);
                            palette.add(context.naturalAt(body,below.getY())==IslandContext.Occupancy.SOLID && NaturalMaterials.skin(value) && sturdy(value)
                                    ?value:palette.getLast());
                        }
                        for(int y=sourceY-2;y<=top;y++) {
                            var at=new BlockPos(x,y,z);var original=natural.state(at);
                            if(protectedCell.test(at) || y<=sourceY && (!NaturalMaterials.skin(original) || !sturdy(original)
                                    || context.naturalAt(body,y)!=IslandContext.Occupancy.SOLID))continue;
                            var target=palette.get(Math.min(2,top-y));if(original.equals(target))continue;
                            var cell=new Cell(at,original,target);var previous=cells.putIfAbsent(at,cell);
                            if(previous!=null && !previous.equals(cell))return new Result(Status.UNKNOWN,List.of(),columns);
                            if(cells.size()>MAX_CELLS)return new Result(Status.BUDGET,List.of(),columns);
                        }
                        break;
                    }
                }
            }
        } catch(StructureTerrainView.Unavailable unavailable) { return new Result(Status.UNKNOWN,List.of(),columns); }
        return new Result(Status.READY,List.copyOf(cells.values()),columns);
    }
    /** Read-only preflight; no partial patch is accepted when a current cell conflicts. */
    public static boolean canApply(List<Cell> cells,ChunkAccess chunk) {
        if(cells.size()>MAX_CELLS || cells.stream().map(Cell::position).distinct().count()!=cells.size())
            throw new IllegalArgumentException("Invalid grading patch cardinality");
        for(var cell:cells) {
            if(!new ChunkPos(cell.position()).equals(chunk.getPos()))throw new IllegalArgumentException("Cross-chunk grading");
            if(cell.position().getY()<chunk.getMinBuildHeight() || cell.position().getY()>=chunk.getMaxBuildHeight())
                throw new IllegalArgumentException("Grading outside build bounds");
            var current=chunk.getBlockState(cell.position());
            if(!current.equals(cell.original()) && !current.equals(cell.target())
                    && !(cell.original().isAir() && NaturalMaterials.clearableDecoration(current))) {
                org.sathrek.sky_archipelago.worldgen.benchmark.StructureBenchmarkRecorder
                        .increment("skyweaveVillageGradeConflict."+current);
                return false;
            }
        }
        return true;
    }
    /** Exact retained intent is mandatory before the first block write, including recovery. */
    public static boolean apply(net.minecraft.server.level.ServerLevel level,List<Cell> cells,ChunkAccess chunk) {
        var snapshot=NaturalStructureInputs.existing(level,chunk);
        if(snapshot==null || snapshot.grading()==null || !snapshot.grading().cells().equals(cells))
            throw new IllegalStateException("Grading requires its exact retained natural-source intent");
        if(!canApply(cells,chunk))return false;
        for(var cell:cells)if(!chunk.getBlockState(cell.position()).equals(cell.target())) {
            var previous=chunk.getBlockState(cell.position());
            chunk.setBlockState(cell.position(),cell.target(),false);
            if(!previous.isAir() && NaturalMaterials.clearableDecoration(previous) && cell.position().getY()+1<chunk.getMaxBuildHeight()
                    && chunk.getBlockState(cell.position().above()).is(previous.getBlock()))
                chunk.markPosForPostprocessing(cell.position().above());
        }
        if(!cells.isEmpty()) { StructureHeights.refresh(chunk);chunk.setUnsaved(true); }
        return true;
    }
}
