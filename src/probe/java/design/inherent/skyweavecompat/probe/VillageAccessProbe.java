package design.inherent.skyweavecompat.probe;

import com.google.gson.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.pathfinder.*;

/** Real native local doorway paths; not village-wide navigation or an admission rule. */
final class VillageAccessProbe {
    private VillageAccessProbe() {}
    private record Port(int piece, String role, net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo info) {}
    private record Connection(Port source,Port target) {}

    /** Same physical X/Z connectors as fitting, retaining their separate declared heights. */
    private static List<Connection> connections(ServerLevel level,StructureStart start) {
        var ports=new ArrayList<Port>();
        for(int i=0;i<start.getPieces().size();i++)if(start.getPieces().get(i) instanceof PoolElementStructurePiece piece) {
            String role=role(level,piece);
            for(var port:piece.getElement().getShuffledJigsawBlocks(level.getServer().getStructureManager(),piece.getPosition(),
                    piece.getRotation(),net.minecraft.util.RandomSource.create(0))) {
                if(JigsawBlock.getFrontFacing(port.state()).getAxis()!=Direction.Axis.Y)ports.add(new Port(i,role,port));
            }
            if(ports.size()>8192)throw new AssertionError("Connector fixture exceeds 8192 ports");
        }
        var byColumn=new HashMap<Long,List<Port>>();
        for(var port:ports)byColumn.computeIfAbsent(net.minecraft.world.level.ChunkPos.asLong(port.info().pos().getX(),port.info().pos().getZ()),
                k->new ArrayList<>()).add(port);
        var result=new ArrayList<Connection>();
        for(var port:ports) {
            var target=port.info().pos().relative(JigsawBlock.getFrontFacing(port.info().state()));
            for(var other:byColumn.getOrDefault(net.minecraft.world.level.ChunkPos.asLong(target.getX(),target.getZ()),List.of())) {
                if(port.piece()>=other.piece() || !JigsawBlock.canAttach(port.info(),other.info()))continue;
                if(!port.role().equals("ROAD")&&!other.role().equals("ROAD"))continue;
                if(Math.abs((long)port.info().pos().getY()-other.info().pos().getY())>4)continue;
                result.add(new Connection(port,other));
            }
        }
        return result;
    }
    private static String role(ServerLevel level,StructurePiece piece) {
        try {
            var method=design.inherent.skyweavecompat.terrain.structure.VillageLayoutRepair.class.getDeclaredMethod("node",StructurePiece.class,
                    net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager.class);
            method.setAccessible(true);var node=method.invoke(null,piece,level.getServer().getStructureManager());
            var role=node.getClass().getDeclaredMethod("role");role.setAccessible(true);return role.invoke(node).toString();
        } catch(ReflectiveOperationException failure) {throw new IllegalStateException("Cannot inspect actual fitter road role",failure);}
    }

    private static SortedSet<BlockPos> doors(ServerLevel level, StructureStart start) {
        var doors = new TreeSet<BlockPos>();
        for (var piece : start.getPieces()) {
            var box = piece.getBoundingBox();
            for (int x=box.minX();x<=box.maxX();x++) for (int z=box.minZ();z<=box.maxZ();z++) {
                var chunk=level.getChunkSource().getChunkNow(x>>4,z>>4);
                if(chunk==null)throw new AssertionError("Door survey requires realized chunks");
                for(int y=box.minY();y<=box.maxY();y++) {
                    var pos=new BlockPos(x,y,z);var state=chunk.getBlockState(pos);
                    if(state.getBlock() instanceof DoorBlock && state.getValue(DoorBlock.HALF)==DoubleBlockHalf.LOWER)
                        doors.add(pos);
                }
            }
        }
        return doors;
    }

    /** Explicit fixture setup; the subsequent path inspection never creates chunks. */
    static JsonObject prepareNeighborhoods(ServerLevel level, StructureStart start) {
        var required=new TreeSet<net.minecraft.world.level.ChunkPos>(Comparator
                .comparingInt((net.minecraft.world.level.ChunkPos p)->p.x).thenComparingInt(p->p.z));
        for(var door:doors(level,start).stream().limit(64).toList()) {
            for(int x=(door.getX()-6)>>4;x<=(door.getX()+6)>>4;x++)
                for(int z=(door.getZ()-6)>>4;z<=(door.getZ()+6)>>4;z++)
                    required.add(new net.minecraft.world.level.ChunkPos(x,z));
        }
        for(var link:connections(level,start).stream().limit(64).toList()) {
            var center=link.source().info().pos();
            for(int x=(center.getX()-6)>>4;x<=(center.getX()+6)>>4;x++)
                for(int z=(center.getZ()-6)>>4;z<=(center.getZ()+6)>>4;z++)
                    required.add(new net.minecraft.world.level.ChunkPos(x,z));
        }
        if(required.size()>256)throw new AssertionError("Door fixture exceeds 256 neighborhood chunks");
        int loaded=0;
        for(var chunk:required)if(level.getChunkSource().getChunkNow(chunk.x,chunk.z)==null) {
            level.getChunk(chunk.x,chunk.z,net.minecraft.world.level.chunk.status.ChunkStatus.FULL);loaded++;
        }
        var result=new JsonObject();result.addProperty("requiredChunks",required.size());
        result.addProperty("explicitlyRealizedChunks",loaded);return result;
    }

    static JsonObject inspect(ServerLevel level, StructureStart start) {
        var doors=doors(level,start);
        var report=new JsonObject();var rows=new JsonArray();report.add("doors",rows);
        report.addProperty("scope","At most 64 actual doorways, native villager-sized two-way paths within 6 blocks; no entities spawned or terrain changes; not whole-village access");
        report.addProperty("totalDoors",doors.size());
        var villager=new Villager(EntityType.VILLAGER,level);
        int before=level.getChunkSource().getLoadedChunksCount(),passed=0;
        try {
            for(var door:doors.stream().limit(64).toList()) {
                var row=new JsonObject();rows.add(row);row.addProperty("door",door.toShortString());
                var facing=level.getBlockState(door).getValue(DoorBlock.FACING);
                var a=door.relative(facing);var b=door.relative(facing,-1);
                var lower=door.offset(-6,-4,-6);var upper=door.offset(6,4,6);
                boolean available=true;
                for(int x=lower.getX()>>4;x<=upper.getX()>>4;x++)for(int z=lower.getZ()>>4;z<=upper.getZ()>>4;z++)
                    available&=level.getChunkSource().getChunkNow(x,z)!=null;
                if(!available){row.addProperty("status","unavailable neighborhood");continue;}
                var region=new PathNavigationRegion(level,lower,upper);
                var aa=stance(region,villager,a);var bb=stance(region,villager,b);
                row.add("positiveSide",describeStance(region,a,aa));row.add("negativeSide",describeStance(region,b,bb));
                if(aa==null || bb==null){row.addProperty("status","no collision-free adjacent stance");continue;}
                var outward=path(villager,region,aa,bb,true);var inward=path(villager,region,bb,aa,true);
                row.add("forward",describe(outward));row.add("reverse",describe(inward));
                boolean reached=outward!=null&&outward.canReach()&&inward!=null&&inward.canReach();
                row.addProperty("bothDirections",reached);if(reached)passed++;
                row.add("withoutDoorOpening",describe(path(villager,region,aa,bb,false)));
            }
        } finally {villager.discard();}
        report.addProperty("twoWayDoors",passed);
        report.addProperty("chunksBefore",before);report.addProperty("chunksAfter",level.getChunkSource().getLoadedChunksCount());
        if(level.getChunkSource().getLoadedChunksCount()!=before)throw new AssertionError("Door path query created chunks");
        return report;
    }

    static JsonObject inspectConnections(ServerLevel level,StructureStart start) {
        var links=connections(level,start);var report=new JsonObject();var rows=new JsonArray();report.add("connections",rows);
        report.addProperty("scope","At most 64 compatible horizontal physical X/Z jigsaw pairs involving fitter-qualified roads, with declared height difference <=4; native villager-sized paths within 6 blocks. No chunks created, mobs spawned or terrain writes; not whole-network access or admission.");
        report.addProperty("totalConnections",links.size());report.addProperty("limit",64);
        int before=level.getChunkSource().getLoadedChunksCount(),passed=0;
        var villager=new Villager(EntityType.VILLAGER,level);
        try {
            for(var link:links.stream().limit(64).toList()) {
                var a=link.source().info();var b=link.target().info();
                var row=new JsonObject();rows.add(row);row.addProperty("sourcePiece",link.source().piece());row.addProperty("targetPiece",link.target().piece());
                row.addProperty("sourceRole",link.source().role());row.addProperty("targetRole",link.target().role());
                row.addProperty("sourcePort",a.pos().toShortString());row.addProperty("targetPort",b.pos().toShortString());
                var center=a.pos();var lower=center.offset(-6,-8,-6);var upper=center.offset(6,8,6);
                boolean available=true;
                for(int x=lower.getX()>>4;x<=upper.getX()>>4;x++)for(int z=lower.getZ()>>4;z<=upper.getZ()>>4;z++)
                    available&=level.getChunkSource().getChunkNow(x,z)!=null;
                if(!available){row.addProperty("status","unavailable neighborhood");continue;}
                var region=new PathNavigationRegion(level,lower,upper);
                var aa=stance(region,villager,a.pos().relative(JigsawBlock.getFrontFacing(a.state()),-2));
                var bb=stance(region,villager,b.pos().relative(JigsawBlock.getFrontFacing(b.state()),-2));
                row.add("sourceStance",describeStance(region,a.pos(),aa));row.add("targetStance",describeStance(region,b.pos(),bb));
                if(aa==null||bb==null){row.addProperty("status","no collision-free connector stance");continue;}
                var forward=path(villager,region,aa,bb,true);var reverse=path(villager,region,bb,aa,true);
                row.add("forward",describe(forward));row.add("reverse",describe(reverse));
                boolean reached=forward!=null&&forward.canReach()&&reverse!=null&&reverse.canReach();
                row.addProperty("bothDirections",reached);if(reached)passed++;
            }
        } finally {villager.discard();}
        report.addProperty("twoWayConnections",passed);report.addProperty("chunksBefore",before);
        report.addProperty("chunksAfter",level.getChunkSource().getLoadedChunksCount());
        if(level.getChunkSource().getLoadedChunksCount()!=before)throw new AssertionError("Connector path query created chunks");
        return report;
    }
    private record Stance(BlockPos pos,double floor) {}
    private static Stance stance(PathNavigationRegion region,Villager mob,BlockPos at) {
        for(int dy:new int[]{0,-1,1,-2,2}) {
            var pos=at.above(dy);var below=pos.below();
            if(region.getBlockState(below).getCollisionShape(region,below).isEmpty()
                    || !region.getFluidState(pos).isEmpty())continue;
            double floor=WalkNodeEvaluator.getFloorLevel(region,pos);
            var box=mob.getDimensions(mob.getPose()).makeBoundingBox(pos.getX()+.5,floor+.001,pos.getZ()+.5);
            boolean free=true;for(var shape:region.getBlockCollisions(mob,box))if(!shape.isEmpty()){free=false;break;}
            if(free)return new Stance(pos,floor);
        }
        return null;
    }
    private static JsonObject describeStance(PathNavigationRegion region,BlockPos pos,Stance stance) {
        var row=new JsonObject();row.addProperty("position",stance==null?null:stance.pos().toShortString());
        if(stance!=null)row.addProperty("floor",stance.floor());
        var blocks=new JsonArray();for(int y=-2;y<=2;y++)blocks.add(region.getBlockState(pos.above(y)).toString());row.add("nearbyBlocks",blocks);
        return row;
    }
    private static Path path(Villager mob,PathNavigationRegion region,Stance from,Stance to,boolean openDoors) {
        mob.moveTo(from.pos().getX()+.5,from.floor(),from.pos().getZ()+.5);mob.setOnGround(true);
        var evaluator=new WalkNodeEvaluator();evaluator.setCanPassDoors(true);evaluator.setCanOpenDoors(openDoors);
        return new PathFinder(evaluator,512).findPath(region,mob,Set.of(to.pos()),12,0,1);
    }
    private static JsonObject describe(Path path) {
        var row=new JsonObject();row.addProperty("reached",path!=null&&path.canReach());
        row.addProperty("nodes",path==null?0:path.getNodeCount());
        if(path!=null){var nodes=new JsonArray();for(int i=0;i<path.getNodeCount();i++)nodes.add(path.getNode(i).asBlockPos().toShortString());row.add("positions",nodes);}
        return row;
    }
}
