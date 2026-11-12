package design.inherent.skyweavecompat.terrain.structure;

import com.google.gson.*;
import design.inherent.skyweavecompat.terrain.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;
import org.sathrek.sky_archipelago.worldgen.benchmark.StructureBenchmarkRecorder;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement.PendingStructurePlacement;

/** Staged FEATURES preparation and completion; retained intent is not a general rollback journal. */
public final class GroundVillageTerrain {
    private static final ThreadLocal<Phase> ACTIVE=new ThreadLocal<>();
    private static final int MAX_REFERENCES=2048;
    private record StartKey(String id,String physical) implements Comparable<StartKey> {
        @Override public int compareTo(StartKey other) { int n=id.compareTo(other.id);return n==0?physical.compareTo(other.physical):n; }
    }
    private record Piece(PoolElementStructurePiece nativePiece,int index,BlockPos reference,StructureStart start) {}
    private record Start(StructureStart nativeStart,JsonObject intent) {}
    private record Reference(Structure structure,long chunk) {}
    private GroundVillageTerrain() {}

    public static void decorate(ChunkGenerator generator,WorldGenLevel level,ChunkAccess chunk,StructureManager structures,Runnable original) {
        var previous=ACTIVE.get();Phase phase=null;
        if(generator instanceof SkyIslandChunkGenerator sky && level instanceof WorldGenRegion
                && structures.shouldGenerateStructures() && NaturalStructureInputs.owned(level.getLevel()) && !SharedConstants.debugVoidTerrain(chunk.getPos()))
            phase=new Phase(sky,level,chunk);
        if(phase==null)ACTIVE.remove();else ACTIVE.set(phase);
        try { original.run();if(phase!=null)phase.finish(); }
        finally { if(previous==null)ACTIVE.remove();else ACTIVE.set(previous); }
    }
    /** Preserve existing natural shallow anchors before classic carving; no fills or acceptance receipts. */
    static Set<BlockPos> carvingProtection(ChunkGenerator generator,WorldGenLevel level,ChunkAccess chunk) {
        if(!(generator instanceof SkyIslandChunkGenerator sky) || !(level instanceof WorldGenRegion)
                || chunk.getPersistedStatus()!=ChunkStatus.SURFACE || !NaturalStructureInputs.owned(level.getLevel())
                || chunk.getAllReferences().isEmpty())return Set.of();
        // Reuse the same bounded, non-creating native-start resolver and processor compiler
        // as FEATURES. A missing source never becomes an invented structure reservation.
        var phase=new Phase(sky,level,chunk);var starts=phase.starts(List.of(chunk));
        if(starts==null){StructureBenchmarkRecorder.increment("skyweaveVillageCarverSourcesUnavailable");return Set.of();}
        if(starts.isEmpty())return Set.of();
        var terrain=StructureTerrainView.capture(level.getSeed(),level.registryAccess(),List.of(chunk));
        var field=(InteriorField)(Object)((GeologicalGenerator)(Object)sky).skyweave$field(level.getLevel().getChunkSource().randomState());
        var contexts=new HashMap<Long,IslandContext>();var cells=new HashSet<BlockPos>();
        var budget=new VillageFootprint.Budget();int index=0;
        int gap=StructureConfiguration.policy().villageFitting().maximumGroundGap();
        for(var value:starts.values()) {
            var start=value.nativeStart();if(start.getStructure().terrainAdaptation()==TerrainAdjustment.NONE)continue;
            var first=phase.physicalBox(start.getPieces().getFirst());var center=first.getCenter();
            var reference=new BlockPos(center.getX(),first.minY(),center.getZ());
            for(var raw:start.getPieces()) {
                int pieceIndex=index++;if(pieceIndex>=VillageFootprint.Budget.MAX_PIECES)return Set.of();
                if(raw.getClass()!=PoolElementStructurePiece.class || !raw.isCloseToChunk(chunk.getPos(),0))continue;
                var piece=(PoolElementStructurePiece)raw;
                var adjustment=piece.getElement() instanceof VillageDelegate delegated
                        ?delegated.skyweave$terrainAdjustment().orElse(start.getStructure().terrainAdaptation()):start.getStructure().terrainAdaptation();
                if(adjustment==TerrainAdjustment.NONE || piece.getElement().getProjection()!=StructureTemplatePool.Projection.RIGID)continue;
                var compiled=VillageFootprint.compile(piece,pieceIndex,reference,level.getLevel().getStructureManager(),terrain,budget,chunk.getPos());
                if(compiled.status()==VillageFootprint.Status.BUDGET)return Set.of();
                if(!compiled.qualified())continue;
                for(var foot:compiled.feet()) {
                    var pos=foot.position();
                    var context=contexts.computeIfAbsent(FoundationSupport.key(pos.getX(),pos.getZ()),
                            key->field.skyweave$contextAt(pos.getX(),pos.getZ(),sky.resolvedSkyIslandSettings()));
                    for(int distance=1;distance<=gap+1;distance++) {
                        var at=pos.below(distance);
                        if(!terrain.contains(at) || !NaturalMaterials.skin(terrain.state(at)) || context.solidOwner(at.getY())==null)continue;
                        if(cells.add(at) && cells.size()>VillageFootprint.Budget.MAX_FEET)return Set.of();
                    }
                }
            }
        }
        StructureBenchmarkRecorder.add("skyweaveVillageCarverProtectedCells",cells.size());
        return Set.copyOf(cells);
    }
    /** Native-body anchor runs after all HEAD hooks, including Sky Arch's reference delivery/adaptation. */
    public static void prepare() { var phase=ACTIVE.get();if(phase!=null)phase.prepare(); }
    /** Known native layouts only; this neither loads chunks nor delivers pending references. */
    public static boolean featureConflict(WorldGenLevel level,BoundingBox writes) {
        var phase=ACTIVE.get();
        if(phase==null || phase.level!=level)return false;
        if(phase.placingPiece)return false;
        if(phase.unknownLayout) {
            StructureBenchmarkRecorder.increment("skyweaveVillageUnknownFeatureRefusal");
            return true;
        }
        for(var protectedBox:phase.featureProtection)if(protectedBox.intersects(writes))return true;
        return false;
    }
    /** Per-cell native replacement tests need no temporary volume allocation. */
    public static boolean featureConflict(WorldGenLevel level,BlockPos position) {
        var phase=ACTIVE.get();
        if(phase==null || phase.level!=level || phase.placingPiece)return false;
        if(phase.unknownLayout) {
            StructureBenchmarkRecorder.increment("skyweaveVillageUnknownFeatureRefusal");
            return true;
        }
        for(var protectedBox:phase.featureProtection)if(protectedBox.isInside(position))return true;
        return false;
    }
    public static BlockPos reference(PoolElementStructurePiece piece,BlockPos original) {
        var phase=ACTIVE.get();return phase==null || phase.support==null?original:phase.references.getOrDefault(piece,original);
    }
    public static void place(PoolElementStructurePiece piece,Runnable original) {
        var phase=ACTIVE.get();if(phase==null) {original.run();return;}
        var previous=phase.actor;var previousClears=phase.authoredClears;int previousPiece=phase.actorPiece;boolean placing=phase.placingPiece;phase.placingPiece=true;
        phase.authoredClears=new HashSet<>();
        phase.actor=phase.support!=null && phase.references.containsKey(piece)?phase.pieceOwners.get(piece):null;
        phase.actorPiece=phase.actor==null?-1:phase.pieceIndexes.get(piece);
        StructureBenchmarkRecorder.increment("skyweaveVillageNativePiece."+(phase.actor==null?"unqualified":"qualified"));
        try {original.run();}finally {phase.actor=previous;phase.actorPiece=previousPiece;phase.placingPiece=placing;phase.authoredClears=previousClears;}
    }
    /** Observe the actual qualified placement chain; preview/getSettings calls never enter here. */
    public static StructurePlaceSettings observeSettings(StructurePlaceSettings settings) {
        var phase=ACTIVE.get();if(phase==null || phase.actor==null || phase.support==null || phase.nativeBudget)return settings;
        if(VillageProcessors.qualify(settings.getProcessors(),phase.level.registryAccess())!=null)return settings;
        return settings.addProcessor(new StructureProcessor() {
            @Override public StructureTemplate.StructureBlockInfo processBlock(LevelReader level,BlockPos origin,BlockPos reference,
                    StructureTemplate.StructureBlockInfo raw,StructureTemplate.StructureBlockInfo current,StructurePlaceSettings placement) {
                if(ACTIVE.get()==phase && phase.requirements.contains(current.pos()) && raw.state().is(net.minecraft.world.level.block.Blocks.AIR)
                        && current.state().is(net.minecraft.world.level.block.Blocks.AIR) && raw.nbt()==null && current.nbt()==null)
                    phase.authoredClears.add(current.pos().immutable());
                return current;
            }
            @Override protected StructureProcessorType<?> getType() {return StructureProcessorType.NOP;}
        });
    }
    /** Successful actual region writes only; ordinary/unknown writers remove own-source credit. */
    public static void wrote(WorldGenLevel level,BlockPos position,BlockState state) {
        var phase=ACTIVE.get();if(phase==null || phase.level!=level || phase.support==null || phase.nativeBudget || !new ChunkPos(position).equals(phase.chunk.getPos()))return;
        if(++phase.nativeWrites>VillageFootprint.Budget.MAX_BLOCKS) {
            phase.nativeOutputs.clear();phase.nativeBudget=true;StructureBenchmarkRecorder.increment("skyweaveVillageNativeOutputBudget");return;
        }
        if(phase.actor==null) {phase.nativeOutputs.remove(position);return;}
        if(!phase.nativeOutputs.containsKey(position) && phase.nativeOutputs.size()>=VillageFootprint.Budget.MAX_FEET) {
            phase.nativeOutputs.clear();phase.nativeBudget=true;StructureBenchmarkRecorder.increment("skyweaveVillageNativeOutputBudget");return;
        }
        phase.nativeOutputs.put(position.immutable(),new VillageFoundation.NativeOutput(phase.actor,phase.actorPiece,state,
                state.is(net.minecraft.world.level.block.Blocks.AIR) && phase.authoredClears.contains(position)));
    }
    /** Projection reads the same prepared heightmap as the current chunk's qualified preview. */
    public static int height(LevelReader level,Heightmap.Types type,int x,int z,java.util.function.IntSupplier original) {
        var phase=ACTIVE.get();
        if(phase==null || phase.actor==null || level!=phase.level || phase.projectedTerrain==null
                || !new ChunkPos(Math.floorDiv(x,16),Math.floorDiv(z,16)).equals(phase.chunk.getPos()))return original.getAsInt();
        StructureBenchmarkRecorder.increment("skyweaveVillageFrozenProjection");
        return phase.projectedTerrain.height(type,x,z);
    }

    private static final class Phase {
        final SkyIslandChunkGenerator generator;
        final WorldGenLevel level;
        final ChunkAccess chunk;
        VillageFoundation.Plan support;
        StructureTerrainView projectedTerrain;
        boolean prepared;
        boolean unknownLayout;
        final List<BoundingBox> featureProtection=new ArrayList<>();
        final Map<PoolElementStructurePiece,BlockPos> references=new IdentityHashMap<>();
        final Map<PoolElementStructurePiece,String> pieceOwners=new IdentityHashMap<>();
        final Map<PoolElementStructurePiece,Integer> pieceIndexes=new IdentityHashMap<>();
        final Map<Integer,String> footOwners=new HashMap<>();
        final Map<BlockPos,VillageFoundation.NativeOutput> nativeOutputs=new HashMap<>();
        Set<BlockPos> authoredClears=Set.of();
        Set<BlockPos> requirements=Set.of();
        String actor;
        boolean placingPiece,nativeBudget;
        int nativeWrites,actorPiece=-1;
        Phase(SkyIslandChunkGenerator generator,WorldGenLevel level,ChunkAccess chunk) {
            this.generator=generator;this.level=level;this.chunk=chunk;
        }
        void prepare() {
            if(prepared)throw new IllegalStateException("Duplicate village terrain preparation");prepared=true;
            var starts=starts(List.of(chunk));
            if(starts==null) { unknownLayout=true;return; }
            var chunks=new ArrayList<ChunkAccess>();var center=chunk.getPos();
            for(int x=center.x-1;x<=center.x+1;x++)for(int z=center.z-1;z<=center.z+1;z++) {
                if(!level.hasChunk(x,z)) {unknownLayout=true;return;}
                var known=level.getChunk(x,z,ChunkStatus.CARVERS,false);
                if(known==null) {unknownLayout=true;return;}chunks.add(known);
            }
            // FEATURES may write radius one. A village in the destination chunk need not
            // be referenced by the feature's origin chunk, including after local FEATURES.
            var protectedStarts=starts(chunks);
            if(protectedStarts==null) {unknownLayout=true;return;}
            int maximumGap=StructureConfiguration.policy().villageFitting().maximumGroundGap();
            for(var startEntry:protectedStarts.entrySet()) {
                var info=startEntry.getValue();
                var start=info.nativeStart();
                if(start.getStructure().terrainAdaptation()==TerrainAdjustment.NONE)continue;
                for(var nativePiece:start.getPieces()) {
                    if(nativePiece.getClass()!=PoolElementStructurePiece.class)continue;
                    var piece=(PoolElementStructurePiece)nativePiece;
                    if(piece.getElement() instanceof VillageDelegate delegated
                            && delegated.skyweave$terrainAdjustment().orElse(start.getStructure().terrainAdaptation())==TerrainAdjustment.NONE)continue;
                    var box=physicalBox(piece);
                    featureProtection.add(new BoundingBox(box.minX()-VillageGrading.HALO,box.minY()-maximumGap-1,box.minZ()-VillageGrading.HALO,
                            box.maxX()+VillageGrading.HALO,box.maxY(),box.maxZ()+VillageGrading.HALO));
                }
            }
            if(starts.isEmpty())return;
            for(var entry:starts.entrySet())for(var piece:entry.getValue().nativeStart().getPieces())
                if(piece.getClass()==PoolElementStructurePiece.class)pieceOwners.put((PoolElementStructurePiece)piece,entry.getKey().physical());
            final StructureTerrainView natural;
            try { natural=NaturalStructureInputs.view(level.getLevel(),chunks); }
            catch(StructureTerrainView.Unavailable missing) { return; }
            var adapted=(GeologicalGenerator)(Object)generator;
            var field=adapted.skyweave$field(level.getLevel().getChunkSource().randomState());
            var settings=generator.resolvedSkyIslandSettings();
            var contexts=new HashMap<Long,IslandContext>();
            java.util.function.BiFunction<Integer,Integer,IslandContext> context=(x,z)->contexts.computeIfAbsent(FoundationSupport.key(x,z),
                    key->((InteriorField)(Object)field).skyweave$contextAt(x,z,settings));
            int gap=StructureConfiguration.policy().villageFitting().maximumGroundGap();
            var budget=new VillageFootprint.Budget();var pieces=new ArrayList<Piece>();var parts=new ArrayList<VillageGrading.Part>();int index=0;
            var halo=new BoundingBox(center.getMinBlockX()-VillageGrading.HALO,chunk.getMinBuildHeight(),center.getMinBlockZ()-VillageGrading.HALO,
                    center.getMaxBlockX()+VillageGrading.HALO,chunk.getMaxBuildHeight()-1,center.getMaxBlockZ()+VillageGrading.HALO);
            for(var info:starts.values()) {
                var start=info.nativeStart();
                if(start.getStructure().terrainAdaptation()==TerrainAdjustment.NONE)continue;
                var first=physicalBox(start.getPieces().getFirst());var c=first.getCenter();var reference=new BlockPos(c.getX(),first.minY(),c.getZ());
                for(var nativePiece:start.getPieces()) {
                    if(index>=VillageFootprint.Budget.MAX_PIECES)return;
                    int current=index++;
                    footOwners.put(current,pieceOwners.get(nativePiece));
                    if(!nativePiece.isCloseToChunk(center,VillageGrading.HALO))continue;
                    // Overrides have independent placement contracts; do not infer native output from their element alone.
                    if(nativePiece.getClass()!=PoolElementStructurePiece.class)continue;
                    var piece=(PoolElementStructurePiece)nativePiece;
                    var adjustment=piece.getElement() instanceof VillageDelegate delegated
                            ?delegated.skyweave$terrainAdjustment().orElse(start.getStructure().terrainAdaptation()):start.getStructure().terrainAdaptation();
                    if(adjustment==TerrainAdjustment.NONE)continue;
                    pieces.add(new Piece(piece,current,reference,start));
                    if(piece.getElement().getProjection()!=StructureTemplatePool.Projection.RIGID)continue;
                    var feet=VillageFootprint.compileArea(piece,current,reference,level.getLevel().getStructureManager(),natural,budget,halo);
                    if(feet.status()==VillageFootprint.Status.BUDGET)return;
                    if(!feet.qualified())continue;
                    var bindings=VillageFoundation.plan(feet.feet(),natural,context,gap);
                    parts.add(new VillageGrading.Part(piece,physicalBox(piece),adjustment,bindings.bindings()));
                }
            }
            var snapshot=field.sampleChunkTerrainSnapshot(center,settings);
            java.util.function.Predicate<BlockPos> protectedCell=at->{
                var data=snapshot.columnDataAt(at.getX(),at.getZ());var river=data.river();var water=data.waterArchetype();
                return river.affected() && at.getY()>=river.solidSurfaceY()-InteriorConfiguration.profile().shellThickness()+1
                        && at.getY()<=Math.max(river.solidSurfaceY(),river.fluidSurfaceY())+1
                        || water.containsWaterAt(at.getY()) || water.containsProtectedSealAt(at.getY());
            };
            var grading=VillageGrading.plan(center,natural,parts,context,protectedCell,gap);
            if(grading.status()!=VillageGrading.Status.READY)return;
            String identity=identity(starts);
            var graded=natural.overlay(grading.cells());
            projectedTerrain=graded;
            var finalFeet=new ArrayList<VillageFootprint.Foot>();var finalBudget=new VillageFootprint.Budget();
            for(var piece:pieces) {
                var result=VillageFootprint.compile(piece.nativePiece(),piece.index(),piece.reference(),level.getLevel().getStructureManager(),graded,finalBudget,center);
                if(result.status()==VillageFootprint.Status.BUDGET)return;
                if(!result.qualified()) {
                    String reason=result.reason();int detail=reason.indexOf(": ");
                    StructureBenchmarkRecorder.increment("skyweaveVillageFootprintRefused."+(detail<0?reason:reason.substring(0,detail)));
                }
                if(result.qualified()) { finalFeet.addAll(result.feet());references.put(piece.nativePiece(),piece.reference());pieceIndexes.put(piece.nativePiece(),piece.index()); }
            }
            var planned=VillageFoundation.afterGrading(VillageFoundation.plan(finalFeet,natural,context,gap),graded);
            // Complete every logical budget/processor decision before granting any write.
            NaturalStructureInputs.bindGrading(level.getLevel(),chunk,identity,grading.cells());
            if(!VillageGrading.apply(level.getLevel(),grading.cells(),chunk))return;
            support=planned;
            requirements=planned.bindings().stream().map(binding->binding.foot().position()).collect(java.util.stream.Collectors.toUnmodifiableSet());
            StructureBenchmarkRecorder.add("skyweaveVillageGradedCells",grading.cells().size());
            StructureBenchmarkRecorder.add("skyweaveVillageProcessedFeet",finalFeet.size());
        }
        void finish() {
            if(support==null)return;
            support=VillageFoundation.finalNativeFeet(support,nativeBudget?Map.of():nativeOutputs,footOwners::get);
            var realized=VillageFoundation.realize(support,chunk,nativeBudget?Map.of():nativeOutputs,footOwners::get);
            GroundSupportProofs.record(level.getLevel(),chunk,support,realized);
            StructureBenchmarkRecorder.add("skyweaveVillageSupportWrites",realized.writes());
            StructureBenchmarkRecorder.add("skyweaveVillageSupportRefusals",realized.outcomes().values().stream()
                    .filter(outcome->!VillageFoundation.successful(outcome) && outcome!=VillageFoundation.Outcome.AUTHORED_CLEAR).count());
            realized.outcomes().values().forEach(outcome->StructureBenchmarkRecorder.increment("skyweaveVillageSupportOutcome."+outcome.name()));
            for(var binding:support.bindings()) {
                var outcome=realized.outcomes().get(binding.foot());
                var at=outcome==VillageFoundation.Outcome.CHANGED_FOOT?binding.foot().position():
                        outcome==VillageFoundation.Outcome.CHANGED_SOURCE?binding.source():null;
                if(at==null)continue;
                var written=nativeOutputs.get(at);String origin=written==null?"unattributed":written.owner().equals(footOwners.get(binding.foot().piece()))?"same_start":"other_start";
                StructureBenchmarkRecorder.increment("skyweaveVillageChanged."+outcome+"."+origin+"."+
                        net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(chunk.getBlockState(at).getBlock()));
            }
        }
        private SortedMap<StartKey,Start> starts(Collection<ChunkAccess> referencedChunks) {
            var registry=level.registryAccess().registryOrThrow(Registries.STRUCTURE);var found=new TreeMap<StartKey,Start>();int count=0,pieceWork=0;long bytes=0;
            var seen=Collections.newSetFromMap(new IdentityHashMap<StructureStart,Boolean>());
            var references=new HashSet<Reference>();
            for(var referencedChunk:referencedChunks)for(var entry:referencedChunk.getAllReferences().entrySet()) {
                if(StructureMembership.family(registry.wrapAsHolder(entry.getKey()))!=StructureFamily.VILLAGES)continue;
                for(long reference:entry.getValue()) {
                    if(!references.add(new Reference(entry.getKey(),reference)))continue;
                    if(++count>MAX_REFERENCES)return null;
                    var pos=new ChunkPos(reference);if(!level.hasChunk(pos.x,pos.z))return null;
                    var owner=level.getChunk(pos.x,pos.z,ChunkStatus.STRUCTURE_STARTS,false);if(owner==null)return null;
                    var start=owner.getStartForStructure(entry.getKey());if(start==null)return null;
                    if(start.isValid() && !PendingStructurePlacement.isRevoked(start) && !start.getPieces().isEmpty()) {
                        if(!seen.add(start))continue;
                        if(start.getPieces().size()>VillageFootprint.Budget.MAX_PIECES-pieceWork)return null;
                        pieceWork+=start.getPieces().size();
                        var id=registry.getKey(entry.getKey());var serialization=StructurePieceSerializationContext.fromLevel(level.getLevel());
                        var tags=new ArrayList<CompoundTag>();
                        try {
                            for(var piece:start.getPieces()) {
                                var tag=piece.createTag(serialization);bytes+=tag.sizeInBytes();
                                if(bytes>8*1024*1024)return null;tags.add(tag);
                            }
                            var physical=PhysicalStructureIdentity.of(id,tags);var intent=intent(start,id,tags);
                            var previous=found.putIfAbsent(new StartKey(id.toString(),physical),new Start(start,intent));
                            if(previous!=null && !previous.intent().equals(intent))return null;
                        } catch(IllegalArgumentException unsupported) { return null; }
                    }
                }
            }
            return found;
        }
        private String identity(SortedMap<StartKey,Start> starts) {
            var root=new JsonObject();
            for(var entry:starts.entrySet())root.add(entry.getKey().physical(),entry.getValue().intent());
            try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(root.toString().getBytes(StandardCharsets.UTF_8))); }
            catch(NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
        }
        private BoundingBox physicalBox(StructurePiece piece) {
            return piece.getClass()==PoolElementStructurePiece.class?((PoolElementStructurePiece)piece).getElement().getBoundingBox(
                    level.getLevel().getStructureManager(),((PoolElementStructurePiece)piece).getPosition(),((PoolElementStructurePiece)piece).getRotation()):piece.getBoundingBox();
        }
        private JsonObject intent(StructureStart start,net.minecraft.resources.ResourceLocation id,List<CompoundTag> tags) {
            var result=new JsonObject();var pieces=new JsonArray();
            for(var tag:tags)pieces.add(PhysicalStructureIdentity.of(id,List.of(tag)));
            result.add("piecesInNativeOrder",pieces);result.addProperty("terrainAdjustment",start.getStructure().terrainAdaptation().getSerializedName());
            var first=physicalBox(start.getPieces().getFirst());var center=first.getCenter();
            var reference=new JsonArray();reference.add(center.getX());reference.add(first.minY());reference.add(center.getZ());result.add("reference",reference);
            return result;
        }
    }
}
