package design.inherent.skyweavecompat.terrain.structure;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import design.inherent.skyweavecompat.mixin.VillagePaletteAccess;
import design.inherent.skyweavecompat.mixin.VillageTemplateAccess;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.JigsawBlock;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.sathrek.sky_archipelago.config.SkyIslandSettings;
import org.sathrek.sky_archipelago.worldgen.benchmark.StructureBenchmarkRecorder;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;
import org.sathrek.sky_archipelago.worldgen.generator.terrain.SkyIslandChunkTerrainSnapshot;

/** Read-only support and connector qualification, shared by native planning and commitment. */
public final class VillageLayoutRepair {
    public enum Role { CENTER, ROAD, BUILDING, FARM, DECORATION }
    private record Geometry(List<FoundationSupport.Foot> feet, boolean farm) {}
    record Node(StructurePiece piece, Role role, List<FoundationSupport.Foot> feet,
            List<StructureTemplate.StructureBlockInfo> ports, boolean projected) {}
    private record Port(int node, StructureTemplate.StructureBlockInfo info) {}
    private static final Cache<StructureTemplate, Geometry> GEOMETRY = CacheBuilder.newBuilder().weakKeys().maximumSize(512).build();
    private static final ThreadLocal<Integer> ORIGINAL_BUILDINGS = new ThreadLocal<>();
    private static final int MAX_PIECES = 2048, MAX_PORTS = 8192;
    private VillageLayoutRepair() {}

    /** Carry one assembly's denominator through grounding, trimming and final grounding. */
    public static <T> T assembly(net.minecraft.world.level.levelgen.structure.StructureStart original,
            StructureTemplateManager templates, java.util.function.Supplier<T> action) {
        var previous = ORIGINAL_BUILDINGS.get();
        ORIGINAL_BUILDINGS.set(buildings(original, templates));
        try { return action.get(); }
        finally {
            if (previous == null) ORIGINAL_BUILDINGS.remove();
            else ORIGINAL_BUILDINGS.set(previous);
        }
    }

    private static int buildings(net.minecraft.world.level.levelgen.structure.StructureStart start,
            StructureTemplateManager templates) {
        return (int)start.getPieces().stream().map(piece -> node(piece, templates))
                .filter(node -> node.role() == Role.BUILDING).count();
    }

    /** Road caps and decoration cannot disguise the loss of inhabited buildings. */
    public static double quality(net.minecraft.world.level.levelgen.structure.StructureStart original,
            net.minecraft.world.level.levelgen.structure.StructureStart retained, StructureTemplateManager templates) {
        int total = buildings(original, templates);
        return total == 0 ? 0 : Math.min(1, (double)buildings(retained, templates) / total);
    }

    static final class Terrain {
        final IslandField field;
        final SkyIslandSettings settings;
        final int limit;
        final Map<ChunkPos, SkyIslandChunkTerrainSnapshot> snapshots = new HashMap<>();
        final Set<Long> columns = new HashSet<>();
        boolean exhausted;
        Terrain(IslandField field, SkyIslandSettings settings, int limit) { this.field=field; this.settings=settings; this.limit=limit; }
        int ground(FoundationSupport.Foot foot) {
            long key=FoundationSupport.key(foot.x(),foot.z());
            if (!columns.contains(key) && columns.size()==limit) { exhausted=true; return FoundationSupport.MISSING; }
            columns.add(key);
            var cp=new ChunkPos(Math.floorDiv(foot.x(),16),Math.floorDiv(foot.z(),16));
            var snapshot=snapshots.computeIfAbsent(cp, p -> field.sampleChunkTerrainSnapshot(p,settings));
            int below=foot.y()-1;
            // Actual solid intervals distinguish an island roof, a cave and empty sky.
            return snapshot.segmentsAt(foot.x(),foot.z()).stream().filter(span -> span.bottomY()<=below)
                    .mapToInt(span -> Math.min(span.topY(),below)).max().orElse(FoundationSupport.MISSING);
        }
    }

    public static List<StructurePiece> retain(List<StructurePiece> pieces, net.minecraft.world.level.levelgen.structure.StructureStart original,
            IslandField field, SkyIslandSettings settings) {
        var scope=StructurePlacementScope.current();
        if (scope==null || scope.family()!=StructureFamily.VILLAGES) return pieces;
        var policy=scope.distribution().villageFitting();
        if (pieces.size()>MAX_PIECES) return List.of();
        var templates=scope.pass().templateManager();
        var terrain=new Terrain(field,settings,policy.maximumColumnProbes());
        var nodes=new ArrayList<Node>(pieces.size());
        var supported=new HashSet<Integer>();
        var assembled = ORIGINAL_BUILDINGS.get();
        int buildings = assembled == null ? buildings(original, templates) : assembled;
        int portCount=0;
        for (var piece:pieces) {
            var node=node(piece,templates);
            nodes.add(node);
            portCount+=node.ports().size();
            if (portCount>MAX_PORTS) return List.of();
            if (node.role()==Role.DECORATION && node.feet().isEmpty()) continue;
            var feet=node.feet();
            if (node.projected()) feet=feet.stream().map(foot -> {
                int y=terrain.ground(new FoundationSupport.Foot(foot.x(),Integer.MAX_VALUE,foot.z()));
                return new FoundationSupport.Foot(foot.x(),y==FoundationSupport.MISSING ? foot.y() : y+1,foot.z());
            }).toList();
            if (FoundationSupport.evaluate(feet,terrain::ground,policy).accepted()) supported.add(nodes.size()-1);
        }
        if (terrain.exhausted) return List.of();
        var ports=new HashMap<Long,List<Port>>();
        for (int i=0;i<nodes.size();i++) if (supported.contains(i))
            for (var info:nodes.get(i).ports()) ports.computeIfAbsent(FoundationSupport.key(info.pos().getX(),info.pos().getZ()),k->new ArrayList<>()).add(new Port(i,info));
        var links=new HashMap<Integer,Set<Integer>>();
        for (var bucket:ports.values()) for (var port:bucket) {
            var a=port.info(); var direction=JigsawBlock.getFrontFacing(a.state());
            var target=a.pos().relative(direction);
            for (var other:ports.getOrDefault(FoundationSupport.key(target.getX(),target.getZ()),List.of())) {
                if (port.node()==other.node() || !JigsawBlock.canAttach(a,other.info())) continue;
                var b=other.info();
                boolean height=Math.abs((long)a.pos().getY()-b.pos().getY())<=policy.maximumGroundGap()+1;
                if (!height && (nodes.get(port.node()).projected() || nodes.get(other.node()).projected())) {
                    int ay=terrain.ground(new FoundationSupport.Foot(a.pos().getX(),Integer.MAX_VALUE,a.pos().getZ()));
                    int by=terrain.ground(new FoundationSupport.Foot(b.pos().getX(),Integer.MAX_VALUE,b.pos().getZ()));
                    height=ay!=FoundationSupport.MISSING && by!=FoundationSupport.MISSING && Math.abs((long)ay-by)<=policy.maximumGroundGap()+1;
                }
                if (height) { links.computeIfAbsent(port.node(),k->new HashSet<>()).add(other.node()); links.computeIfAbsent(other.node(),k->new HashSet<>()).add(port.node()); }
            }
        }
        Set<Integer> retained=Set.of();
        for (int i=0;i<nodes.size();i++) if (supported.contains(i) && nodes.get(i).role()==Role.CENTER) {
            var component=new HashSet<Integer>(); var queue=new ArrayDeque<Integer>(); queue.add(i);component.add(i);
            while (!queue.isEmpty()) for (int next:links.getOrDefault(queue.removeFirst(),Set.of())) if(component.add(next))queue.add(next);
            long houses=component.stream().filter(index->nodes.get(index).role()==Role.BUILDING).count();
            long previous=retained.stream().filter(index->nodes.get(index).role()==Role.BUILDING).count();
            if (houses>previous || houses==previous && component.size()>retained.size()) retained=component;
        }
        var result=new ArrayList<StructurePiece>();
        int retainedBuildings=0;
        for (int i=0;i<nodes.size();i++) if (retained.contains(i)) {
            result.add(nodes.get(i).piece());
            if(nodes.get(i).role()==Role.BUILDING)retainedBuildings++;
        }
        if (buildings==0 || retainedBuildings==0 || retainedBuildings<buildings*policy.minimumRetainedBuildings()) result.clear();
        if (!result.isEmpty()) for (int i=0;i<nodes.size();i++) if (!retained.contains(i) && nodes.get(i).role()==Role.DECORATION) {
            var box=nodes.get(i).piece().getBoundingBox(); int x=box.getCenter().getX(),z=box.getCenter().getZ();
            if (retained.stream().anyMatch(index -> { var host=nodes.get(index).piece().getBoundingBox(); return x>=host.minX()-2 && x<=host.maxX()+2 && z>=host.minZ()-2 && z<=host.maxZ()+2; }))
                result.add(nodes.get(i).piece());
        }
        StructureBenchmarkRecorder.add("skyweaveVillageFoundationColumns",terrain.columns.size());
        StructureBenchmarkRecorder.add("skyweaveVillageRemovedPieces",pieces.size()-result.size());
        return result;
    }

    static Node node(StructurePiece piece,StructureTemplateManager templates) {
        if (!(piece instanceof PoolElementStructurePiece pool)) return new Node(piece,Role.DECORATION,List.of(),List.of(),false);
        StructurePoolElement element=pool.getElement();
        for(int depth=0;element instanceof VillageDelegate delegate && depth<8;depth++)element=delegate.skyweave$delegate();
        if (!(element instanceof SinglePoolElement single)) return new Node(piece,Role.DECORATION,List.of(),List.of(),false);
        var access=(VillageTemplateAccess)single;
        var template=access.skyweave$template(templates);
        var geometry=GEOMETRY.getIfPresent(template);
        if(geometry==null) { geometry=geometry(template); GEOMETRY.put(template,geometry); }
        String path=access.skyweave$template().left().map(id->id.getPath()).orElse("");
        var role=role(path,geometry);
        var placement=new StructurePlaceSettings().setRotation(pool.getRotation());
        var feet=geometry.feet().stream().map(foot -> {
            var pos=StructureTemplate.calculateRelativePosition(placement,new BlockPos(foot.x(),foot.y(),foot.z())).offset(pool.getPosition());
            return new FoundationSupport.Foot(pos.getX(),pos.getY(),pos.getZ());
        }).toList();
        var ports=pool.getElement().getShuffledJigsawBlocks(templates,pool.getPosition(),pool.getRotation(),net.minecraft.util.RandomSource.create(0));
        return new Node(piece,role,feet,ports,pool.getElement().getProjection()==StructureTemplatePool.Projection.TERRAIN_MATCHING);
    }

    private static Role role(String path,Geometry geometry) {
        if(path.contains("town_center") || path.contains("meeting_point"))return Role.CENTER;
        if(path.contains("/roads/") || path.contains("/streets/") || path.contains("/terminator"))return Role.ROAD;
        if(path.contains("/house") || path.contains("/jobsite/"))return Role.BUILDING;
        if(geometry.farm())return Role.FARM;
        return Role.DECORATION;
    }

    private static Geometry geometry(StructureTemplate template) {
        var columns=new HashMap<Long,FoundationSupport.Foot>(); boolean farm=false;
        // Palette alternatives share geometry; conservatively include their structural footing.
        for(var palette:((VillagePaletteAccess)template).skyweave$palettes()) for(var info:palette.blocks()) {
            var state=info.state(); farm|=state.is(Blocks.FARMLAND);
            if(!state.blocksMotion() || !state.getFluidState().isEmpty() || state.is(Blocks.JIGSAW) || state.is(Blocks.STRUCTURE_BLOCK)
                    || state.is(Blocks.STRUCTURE_VOID) || state.is(net.minecraft.tags.BlockTags.LEAVES))continue;
            var pos=info.pos(); long key=FoundationSupport.key(pos.getX(),pos.getZ()); var old=columns.get(key);
            if(old==null || pos.getY()<old.y())columns.put(key,new FoundationSupport.Foot(pos.getX(),pos.getY(),pos.getZ()));
        }
        int base=columns.values().stream().mapToInt(FoundationSupport.Foot::y).min().orElse(0);
        return new Geometry(columns.values().stream().filter(foot->foot.y()<=base+2)
                .sorted(Comparator.comparingInt(FoundationSupport.Foot::x).thenComparingInt(FoundationSupport.Foot::z)).toList(),farm);
    }
}
