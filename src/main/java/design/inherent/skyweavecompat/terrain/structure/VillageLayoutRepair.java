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
    private record Geometry(List<FoundationSupport.Foot> feet, boolean farm,long plane) {}
    record Node(StructurePiece piece, Role role, List<FoundationSupport.Foot> feet,
            List<StructureTemplate.StructureBlockInfo> ports, boolean projected) {}
    private record Port(int node, StructureTemplate.StructureBlockInfo info) {}
    private static final Cache<StructureTemplate.Palette, Geometry> GEOMETRY = CacheBuilder.newBuilder().weakKeys().maximumSize(512).build();
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
        final Map<Long,design.inherent.skyweavecompat.terrain.IslandContext> contexts=new HashMap<>();
        final Map<String,Integer> causes=VillageDecisionTrace.enabled()?new TreeMap<>():null;
        final List<Object> examples=VillageDecisionTrace.enabled()?new ArrayList<>():null;
        final int maximumGap;
        boolean exhausted;
        Terrain(IslandField field, SkyIslandSettings settings, int limit,int maximumGap) {
            this.field=field;this.settings=settings;this.limit=limit;this.maximumGap=maximumGap;
        }
        int ground(FoundationSupport.Foot foot) {
            long key=FoundationSupport.key(foot.x(),foot.z());
            if (!columns.contains(key) && columns.size()==limit) { exhausted=true;note(foot,null,"COLUMN_BUDGET");return FoundationSupport.MISSING; }
            columns.add(key);
            var cp=new ChunkPos(Math.floorDiv(foot.x(),16),Math.floorDiv(foot.z(),16));
            var snapshot=snapshots.computeIfAbsent(cp, p -> field.sampleChunkTerrainSnapshot(p,settings));
            int below=foot.y()-1;
            // Actual solid intervals distinguish an island roof, a cave and empty sky.
            int ground=snapshot.segmentsAt(foot.x(),foot.z()).stream().filter(span -> span.bottomY()<=below)
                    .mapToInt(span -> Math.min(span.topY(),below)).max().orElse(FoundationSupport.MISSING);
            long gap=(long)below-ground;
            if(ground==FoundationSupport.MISSING)note(foot,null,"NO_SOLID_BELOW");
            else if(foot.y()!=Integer.MAX_VALUE && gap>maximumGap)note(foot,ground,"GAP_TOO_DEEP");
            if(ground==FoundationSupport.MISSING || gap<1 || gap>8)return ground;
            var context=contexts.computeIfAbsent(key,k->((design.inherent.skyweavecompat.terrain.InteriorField)(Object)field)
                    .skyweave$contextAt(foot.x(),foot.z(),settings));
            // No owned context when generation adaptations are disabled: retain the upstream
            // heuristic, without claiming material-stage qualification for that installation.
            if(context.bodies().isEmpty())return ground;
            var kind=VillageSupportEnvelope.gap(context,ground,foot.y());
            return switch(kind) {
                case OPEN,CAP -> ground;
                case WATER,CAVITY,OCCUPIED,UNKNOWN -> {note(foot,ground,kind.name());yield FoundationSupport.MISSING;}
            };
        }
        void resetTrace() {if(causes!=null){causes.clear();examples.clear();}}
        void note(FoundationSupport.Foot foot,Integer ground,String cause) {
            if(causes==null)return;
            causes.merge(cause,1,Integer::sum);
            if(examples.size()<8) {
                var row=new LinkedHashMap<String,Object>();row.put("x",foot.x());row.put("z",foot.z());
                row.put("y",foot.y()==Integer.MAX_VALUE?null:foot.y());row.put("ground",ground);row.put("cause",cause);examples.add(row);
            }
        }
    }

    public static List<StructurePiece> retain(List<StructurePiece> pieces, net.minecraft.world.level.levelgen.structure.StructureStart original,
            IslandField field, SkyIslandSettings settings) {
        var scope=StructurePlacementScope.current();
        if (scope==null || scope.family()!=StructureFamily.VILLAGES) return pieces;
        var policy=scope.distribution().villageFitting();
        if (pieces.size()>MAX_PIECES) {
            VillageDecisionTrace.emit(original,"layout_support","PIECE_BUDGET",Map.of("pieces",pieces.size(),"limit",MAX_PIECES));return List.of();
        }
        var templates=scope.pass().templateManager();
        var terrain=new Terrain(field,settings,policy.maximumColumnProbes(),policy.maximumGroundGap());
        var nodes=new ArrayList<Node>(pieces.size());
        var supported=new HashSet<Integer>();
        var assembled = ORIGINAL_BUILDINGS.get();
        int buildings = assembled == null ? buildings(original, templates) : assembled;
        int portCount=0;
        for (var piece:pieces) {
            var node=node(piece,templates);
            terrain.resetTrace();
            nodes.add(node);
            portCount+=node.ports().size();
            if (portCount>MAX_PORTS) {
                VillageDecisionTrace.emit(original,"layout_support","PORT_BUDGET",Map.of("ports",portCount,"limit",MAX_PORTS));return List.of();
            }
            if (node.role()==Role.DECORATION && node.feet().isEmpty()) continue;
            var feet=node.feet();
            if (node.projected()) feet=feet.stream().map(foot -> {
                int y=terrain.ground(new FoundationSupport.Foot(foot.x(),Integer.MAX_VALUE,foot.z()));
                return new FoundationSupport.Foot(foot.x(),y==FoundationSupport.MISSING ? foot.y() : y+1,foot.z());
            }).toList();
            boolean complete=policy.requireCompleteBuildingSupport()
                    && (node.role()==Role.CENTER || node.role()==Role.BUILDING || node.role()==Role.FARM);
            var evaluated=FoundationSupport.evaluate(feet,terrain::ground,policy,complete);
            if (evaluated.accepted()) supported.add(nodes.size()-1);
            else {
                StructureBenchmarkRecorder.increment("skyweaveVillageUnsupported."+node.role());
                if(VillageDecisionTrace.enabled())VillageDecisionTrace.emit(original,"piece_support",
                        terrain.exhausted?"COLUMN_BUDGET":evaluated.columns()==0?"MISSING_FOOTPRINT":terrain.causes.containsKey("UNKNOWN")?"UNKNOWN_TERRAIN":"REJECTED",Map.of(
                        "piece",nodes.size()-1,"role",node.role(),"template",templateName(node.piece()),"bounds",piece.getBoundingBox().toString(),
                        "support",Map.of("columns",evaluated.columns(),"supported",evaluated.supported(),"requireComplete",complete,
                                "unsupportedDepth",evaluated.unsupportedDepth()==Integer.MAX_VALUE?"unresolved":evaluated.unsupportedDepth(),
                                "maximumGap",policy.maximumGroundGap(),"minimumCoverage",policy.minimumSupportFraction(),
                                "maximumUnsupportedDepth",policy.maximumUnsupportedDepth()),
                        "projected",node.projected(),"causes",terrain.causes,"examples",terrain.examples,"exampleLimit",8));
            }
        }
        if (terrain.exhausted) {
            VillageDecisionTrace.emit(original,"layout_support","COLUMN_BUDGET",Map.of("queried",terrain.columns.size(),"limit",terrain.limit));return List.of();
        }
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
        if(supported.stream().noneMatch(index->nodes.get(index).role()==Role.CENTER)) {
            StructureBenchmarkRecorder.increment("skyweaveVillageFit.no_supported_center");
            VillageDecisionTrace.emit(original,"layout_support","NO_SUPPORTED_CENTER",Map.of("pieces",nodes.size(),"supportedPieces",supported.size()));
        }
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
        if (buildings==0 || retainedBuildings==0 || retainedBuildings<buildings*policy.minimumRetainedBuildings()) {
            VillageDecisionTrace.emit(original,"layout_support","RETAINED_BUILDINGS",Map.of("original",buildings,
                    "retained",retainedBuildings,"requiredFraction",policy.minimumRetainedBuildings()));result.clear();
        }
        if (!result.isEmpty()) for (int i=0;i<nodes.size();i++) if (!retained.contains(i) && nodes.get(i).role()==Role.DECORATION
                && (nodes.get(i).feet().isEmpty() || supported.contains(i))) {
            // Proximity may retain footless authored decoration; it cannot overturn
            // an explicit unsupported-foot decision and float a statue into the void.
            var box=nodes.get(i).piece().getBoundingBox(); int x=box.getCenter().getX(),z=box.getCenter().getZ();
            if (retained.stream().anyMatch(index -> { var host=nodes.get(index).piece().getBoundingBox(); return x>=host.minX()-2 && x<=host.maxX()+2 && z>=host.minZ()-2 && z<=host.maxZ()+2; }))
                result.add(nodes.get(i).piece());
        }
        StructureBenchmarkRecorder.add("skyweaveVillageFoundationColumns",terrain.columns.size());
        StructureBenchmarkRecorder.add("skyweaveVillageRemovedPieces",pieces.size()-result.size());
        return result;
    }

    private static String templateName(StructurePiece piece) {
        if(!(piece instanceof PoolElementStructurePiece pool))return piece.getClass().getName();
        var element=pool.getElement();
        for(int i=0;i<8 && element instanceof VillageDelegate delegate;i++)element=delegate.skyweave$delegate();
        return element instanceof SinglePoolElement single?((VillageTemplateAccess)single).skyweave$template().left()
                .map(Object::toString).orElse("inline"):element.getClass().getName();
    }

    static Node node(StructurePiece piece,StructureTemplateManager templates) {
        if (!(piece instanceof PoolElementStructurePiece pool)) return new Node(piece,Role.DECORATION,List.of(),List.of(),false);
        StructurePoolElement element=pool.getElement();
        for(int depth=0;element instanceof VillageDelegate delegate && depth<8;depth++)element=delegate.skyweave$delegate();
        if (!(element instanceof SinglePoolElement single)) return new Node(piece,Role.DECORATION,List.of(),List.of(),false);
        var access=(VillageTemplateAccess)single;
        var template=access.skyweave$template(templates);
        var palettes=((VillagePaletteAccess)template).skyweave$palettes();
        if(palettes.isEmpty())return new Node(piece,Role.DECORATION,List.of(),List.of(),false);
        var palette=access.skyweave$settings(pool.getRotation(),piece.getBoundingBox(),
                net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings.APPLY_WATERLOGGING,false)
                .getRandomPalette(palettes,pool.getPosition());
        long plane=(long)single.getGroundLevelDelta()-1;
        var geometry=GEOMETRY.getIfPresent(palette);
        if(geometry==null || geometry.plane()!=plane) { geometry=geometry(palette,plane); GEOMETRY.put(palette,geometry); }
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

    private static Geometry geometry(StructureTemplate.Palette palette,long plane) {
        boolean farm=palette.blocks().stream().anyMatch(block->block.state().is(Blocks.FARMLAND));
        var feet=VillageFootprint.roots(palette.blocks(),plane).stream().map(block->
                new FoundationSupport.Foot(block.pos().getX(),block.pos().getY(),block.pos().getZ())).toList();
        return new Geometry(feet,farm,plane);
    }
}
