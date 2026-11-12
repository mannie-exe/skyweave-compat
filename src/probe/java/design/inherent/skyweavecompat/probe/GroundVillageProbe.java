package design.inherent.skyweavecompat.probe;

import com.google.gson.*;
import design.inherent.skyweavecompat.Feature;
import design.inherent.skyweavecompat.OptionalIntegrations;
import design.inherent.skyweavecompat.terrain.*;
import design.inherent.skyweavecompat.terrain.structure.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pools.*;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.storage.LevelResource;
import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;
import org.sathrek.sky_archipelago.worldgen.generator.field.model.IslandFamily;

/** Controlled native start on real island terrain; native FEATURES performs placement, grading and shallow support. */
public final class GroundVillageProbe {
    private static volatile Map<BlockPos,BlockState> watched=Map.of();
    private static final java.util.concurrent.ConcurrentLinkedQueue<JsonObject> CHANGES=new java.util.concurrent.ConcurrentLinkedQueue<>();
    private static final java.util.concurrent.atomic.AtomicInteger CHANGE_COUNT=new java.util.concurrent.atomic.AtomicInteger();
    private static final java.util.concurrent.atomic.AtomicBoolean BLOCKED_FEATURE_TESTED=new java.util.concurrent.atomic.AtomicBoolean();
    private static final java.util.concurrent.atomic.AtomicBoolean UNRELATED_FEATURE_TESTED=new java.util.concurrent.atomic.AtomicBoolean();
    private static final java.util.concurrent.atomic.AtomicBoolean NEIGHBOR_FEATURE_TESTED=new java.util.concurrent.atomic.AtomicBoolean();
    private static final JsonObject FEATURE_CONTROLS=new JsonObject();
    private static final Map<ChunkPos,NaturalStructureInputs.Journal> PREPARED=new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<ChunkPos,Integer> PREPARATION_CONTROLS=new java.util.concurrent.ConcurrentHashMap<>();
    private GroundVillageProbe() {}
    private static void check(boolean valid,String message) { if(!valid)throw new AssertionError(message); }
    /** Probe-only exact-foot attribution, bounded and armed after the initial native terrain stages. */
    public static void recordWrite(BlockPos at,BlockState state) {
        var expected=watched.get(at);if(expected==null || expected.equals(state) || CHANGE_COUNT.getAndIncrement()>=128)return;
        var row=new JsonObject();row.addProperty("position",at.toShortString());row.addProperty("expected",expected.toString());row.addProperty("written",state.toString());
        row.add("stack",new Gson().toJsonTree(StackWalker.getInstance().walk(frames->frames.limit(20).map(StackWalker.StackFrame::toString).toList())));
        CHANGES.add(row);
    }
    private static final class CountedRandom extends net.minecraft.world.level.levelgen.LegacyRandomSource {
        int draws;
        CountedRandom() { super(42L); }
        @Override public int nextInt(int bound) { draws++;return super.nextInt(bound); }
        @Override public double nextDouble() { draws++;return super.nextDouble(); }
    }
    /** Real native feature calls inside an isolated FEATURES region; no product testing authority. */
    public static synchronized void featureControls(net.minecraft.world.level.WorldGenLevel level,ChunkAccess chunk,
            net.minecraft.world.level.chunk.ChunkGenerator generator) {
        if(watched.isEmpty() || !OptionalIntegrations.enabled(Feature.GROUND_SUPPORT))return;
        var origin=watched.keySet().stream().filter(p->new ChunkPos(p).equals(chunk.getPos())).sorted(BlockPos::compareTo).findFirst().orElse(null);
        var retained=NaturalStructureInputs.existing(level.getLevel(),chunk);
        if(origin!=null && retained!=null && retained.grading()!=null && PREPARED.putIfAbsent(chunk.getPos(),retained.grading())==null) {
            try { if(!Boolean.getBoolean("neighbors.probe.groundLagoon"))
                PREPARATION_CONTROLS.put(chunk.getPos(),controls(level.getLevel(),chunk,retained.grading())); }
            catch(Exception failure) { throw new IllegalStateException("Native preparation controls failed",failure); }
        }
        if(!featureControlsEnabled())return;
        if(origin!=null && !BLOCKED_FEATURE_TESTED.get()) {
            var envelope=new BoundingBox(origin.getX()-3,origin.getY()-4,origin.getZ()-3,origin.getX()+15,origin.getY()+3,origin.getZ()+15);
            if(GroundVillageTerrain.featureConflict(level,envelope) && BLOCKED_FEATURE_TESTED.compareAndSet(false,true)) {
                check(retained!=null && level instanceof net.minecraft.server.level.WorldGenRegion
                        && chunk.getPersistedStatus()!=ChunkStatus.SURFACE,"Late carving negative did not reach the real later stage");
                try {
                    var protection=GroundVillageTerrain.class.getDeclaredMethod("carvingProtection",net.minecraft.world.level.chunk.ChunkGenerator.class,
                            net.minecraft.world.level.WorldGenLevel.class,ChunkAccess.class);
                    protection.setAccessible(true);
                    check(((java.util.Set<?>)protection.invoke(null,generator,level,chunk)).isEmpty(),"Later feature terrain granted a new carving reservation");
                    check(NaturalStructureInputs.existing(level.getLevel(),chunk)==retained,"Late carving query mutated retained natural inputs");
                    FEATURE_CONTROLS.addProperty("lateCarvingSourceRefused",true);
                } catch(ReflectiveOperationException failure) {throw new IllegalStateException("Late carving input control failed",failure);}
                var before=new HashMap<BlockPos,BlockState>();for(var at:BlockPos.betweenClosed(envelope.minX(),envelope.minY(),envelope.minZ(),envelope.maxX(),envelope.maxY(),envelope.maxZ()))before.put(at.immutable(),level.getBlockState(at));
                var random=new CountedRandom();
                boolean boulder=net.minecraft.world.level.levelgen.feature.Feature.FOREST_ROCK.place(
                        new net.minecraft.world.level.levelgen.feature.configurations.BlockStateConfiguration(net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()),level,generator,random,origin);
                check(!boulder && random.draws==0,"Protected boulder consumed randomness or placed");
                random=new CountedRandom();
                boolean lake=net.minecraft.world.level.levelgen.feature.Feature.LAKE.place(new net.minecraft.world.level.levelgen.feature.LakeFeature.Configuration(
                                net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider.simple(net.minecraft.world.level.block.Blocks.LAVA),
                                net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider.simple(net.minecraft.world.level.block.Blocks.STONE)),level,generator,random,origin);
                check(!lake && random.draws==0,"Protected lake consumed randomness or placed");
                protectedSoilAndPlant(level,generator,origin);
                for(var type:List.of("oak","bush","acacia","mega_jungle")) {
                    var treesBefore=treeRefusals(level);
                    check(!net.minecraft.world.level.levelgen.feature.Feature.TREE.place(treeConfiguration(type),level,generator,new CountedRandom(),origin)
                            && treeRefusals(level)>treesBefore,"Native "+type+" did not refuse the whole protected candidate");
                    FEATURE_CONTROLS.addProperty("protectedTree_"+type,true);
                }
                before.forEach((at,state)->check(level.getBlockState(at).equals(state),"Refused feature wrote partial output: "+at));
                FEATURE_CONTROLS.addProperty("protectedBoulderDraws",0);FEATURE_CONTROLS.addProperty("protectedLakeDraws",0);
                FEATURE_CONTROLS.addProperty("protectedDiskAndPlant",true);
                FEATURE_CONTROLS.addProperty("protectedTree",true);
                FEATURE_CONTROLS.addProperty("unchangedProtectedCells",before.size());
            }
        }
        if(!NEIGHBOR_FEATURE_TESTED.get()) {
            var target=watched.keySet().stream().filter(p->new ChunkPos(p).getChessboardDistance(chunk.getPos())==1)
                    .sorted(BlockPos::compareTo).findFirst().orElse(null);
            if(target!=null && NEIGHBOR_FEATURE_TESTED.compareAndSet(false,true)) {
                protectedSoilAndPlant(level,generator,target);
                FEATURE_CONTROLS.addProperty("neighborOriginDiskAndPlant",true);
            }
        }
        if(!UNRELATED_FEATURE_TESTED.get()) {
            var cp=chunk.getPos();int x=cp.getMiddleBlockX(),z=cp.getMiddleBlockZ();
            int y=chunk.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG,x&15,z&15)+1;
            for(int i=0;i<8 && y>chunk.getMinBuildHeight()+3 && NaturalMaterials.clearableDecoration(level.getBlockState(new BlockPos(x,y-1,z)));i++)y--;
            var at=new BlockPos(x,y,z);var box=new BoundingBox(x-3,y-3,z-3,x+1,y+1,z+1);
            if(y>chunk.getMinBuildHeight()+3 && NaturalMaterials.skin(level.getBlockState(at.below()))
                    && !GroundVillageTerrain.featureConflict(level,box) && UNRELATED_FEATURE_TESTED.compareAndSet(false,true)) {
                var random=new CountedRandom();
                boolean placed=net.minecraft.world.level.levelgen.feature.Feature.FOREST_ROCK.place(
                        new net.minecraft.world.level.levelgen.feature.configurations.BlockStateConfiguration(net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()),level,generator,random,at);
                check(placed && random.draws>0 && level.getBlockState(at).is(net.minecraft.world.level.block.Blocks.STONE),"Unrelated native boulder suppressed");
                int boulderDraws=random.draws;
                var disk=new net.minecraft.world.level.levelgen.feature.configurations.DiskConfiguration(
                        net.minecraft.world.level.levelgen.feature.stateproviders.RuleBasedBlockStateProvider.simple(net.minecraft.world.level.block.Blocks.COARSE_DIRT),
                        net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate.alwaysTrue(),net.minecraft.util.valueproviders.ConstantInt.of(0),0);
                check(net.minecraft.world.level.levelgen.feature.Feature.DISK.place(disk,level,generator,random,at)
                        && level.getBlockState(at).is(net.minecraft.world.level.block.Blocks.COARSE_DIRT),"Unrelated native disk suppressed");
                check(net.minecraft.world.level.levelgen.feature.Feature.SIMPLE_BLOCK.place(plantConfiguration(),level,generator,random,at.above())
                        && level.getBlockState(at.above()).is(net.minecraft.world.level.block.Blocks.SUNFLOWER)
                        && level.getBlockState(at.above(2)).is(net.minecraft.world.level.block.Blocks.SUNFLOWER),"Unrelated native double plant suppressed");
                var ore=new net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration(
                        new net.minecraft.world.level.levelgen.structure.templatesystem.BlockMatchTest(net.minecraft.world.level.block.Blocks.STONE),
                        net.minecraft.world.level.block.Blocks.DIAMOND_ORE.defaultBlockState(),16);
                var oreOrigin=java.util.stream.IntStream.rangeClosed(2,24).mapToObj(at::below)
                        .filter(p->level.getBlockState(p).is(net.minecraft.world.level.block.Blocks.STONE)).findFirst().orElseThrow();
                check(net.minecraft.world.level.levelgen.feature.Feature.ORE.place(ore,level,generator,random,oreOrigin),"Unrelated native ore vein suppressed");
                FEATURE_CONTROLS.addProperty("unrelatedBoulderDraws",boulderDraws);FEATURE_CONTROLS.addProperty("unrelatedBoulderPlaced",true);
                FEATURE_CONTROLS.addProperty("unrelatedDiskAndPlant",true);
                FEATURE_CONTROLS.addProperty("unrelatedOrePlaced",true);
                var treeOrigin=at.above(32);
                var treeBounds=new BoundingBox(treeOrigin.getX()-9,treeOrigin.getY()-2,treeOrigin.getZ()-9,
                        treeOrigin.getX()+9,treeOrigin.getY()+8,treeOrigin.getZ()+9);
                var savedTree=new HashMap<BlockPos,BlockState>();
                for(var p:BlockPos.betweenClosed(treeBounds.minX(),treeBounds.minY(),treeBounds.minZ(),treeBounds.maxX(),treeBounds.maxY(),treeBounds.maxZ()))
                    savedTree.put(p.immutable(),level.getBlockState(p));
                try {
                    for(var type:List.of("oak","bush","acacia","mega_jungle")) {
                        savedTree.keySet().forEach(p->level.setBlock(p,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),19));
                        level.setBlock(treeOrigin.below(),net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState(),19);
                        var refusals=treeRefusals(level);
                        check(net.minecraft.world.level.levelgen.feature.Feature.TREE.place(treeConfiguration(type),level,generator,new CountedRandom(),treeOrigin)
                                && savedTree.keySet().stream().anyMatch(p->level.getBlockState(p).is(net.minecraft.world.level.block.Blocks.OAK_LOG))
                                && treeRefusals(level)==refusals,"Unrelated higher-layer native "+type+" suppressed");
                        FEATURE_CONTROLS.addProperty("unrelatedHigherTree_"+type,true);
                    }
                    FEATURE_CONTROLS.addProperty("unrelatedHigherTreePlaced",true);
                } finally {savedTree.forEach((p,state)->level.setBlock(p,state,19));}
            }
        }
    }
    private static net.minecraft.world.level.levelgen.feature.configurations.SimpleBlockConfiguration plantConfiguration() {
        var state=net.minecraft.world.level.block.Blocks.SUNFLOWER.defaultBlockState();
        return new net.minecraft.world.level.levelgen.feature.configurations.SimpleBlockConfiguration(
                new net.minecraft.world.level.levelgen.feature.stateproviders.WeightedStateProvider(
                        net.minecraft.util.random.SimpleWeightedRandomList.<BlockState>builder().add(state,1).add(state,1)));
    }
    private static void protectedSoilAndPlant(net.minecraft.world.level.WorldGenLevel level,
            net.minecraft.world.level.chunk.ChunkGenerator generator,BlockPos at) {
        var before=List.of(level.getBlockState(at),level.getBlockState(at.above()),level.getBlockState(at.below()));var random=new CountedRandom();
        var disk=new net.minecraft.world.level.levelgen.feature.configurations.DiskConfiguration(
                net.minecraft.world.level.levelgen.feature.stateproviders.RuleBasedBlockStateProvider.simple(net.minecraft.world.level.block.Blocks.COARSE_DIRT),
                net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate.alwaysTrue(),net.minecraft.util.valueproviders.UniformInt.of(0,1),0);
        check(!net.minecraft.world.level.levelgen.feature.Feature.DISK.place(disk,level,generator,random,at)
                && random.draws==0,"Protected disk consumed randomness or placed");
        check(!net.minecraft.world.level.levelgen.feature.Feature.SIMPLE_BLOCK.place(plantConfiguration(),level,generator,random,at)
                && random.draws==0,"Protected plant consumed randomness or placed");
        var ore=new net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration(
                net.minecraft.world.level.levelgen.structure.templatesystem.AlwaysTrueTest.INSTANCE,
                net.minecraft.world.level.block.Blocks.DIAMOND_ORE.defaultBlockState(),16);
        long previousOreRefusals=oreRefusals(level);
        net.minecraft.world.level.levelgen.feature.Feature.ORE.place(ore,level,generator,random,at);
        check(oreRefusals(level)>previousOreRefusals,"Native protected ore did not exercise reserved-cell rejection");
        check(before.equals(List.of(level.getBlockState(at),level.getBlockState(at.above()),level.getBlockState(at.below()))),"Refused disk/plant/ore changed target cells");
        FEATURE_CONTROLS.addProperty("protectedOreCells",oreRefusals(level));
    }
    private static long oreRefusals(net.minecraft.world.level.WorldGenLevel level) {
        return org.sathrek.sky_archipelago.worldgen.benchmark.StructureBenchmarkRecorder
                .report("ground-village",level.getSeed(),List.of(),Map.of()).summary().counters().getOrDefault("skyweaveVillageOreCellRefused",0L);
    }
    private static net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration treeConfiguration(String kind) {
        return new net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration.TreeConfigurationBuilder(
                net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider.simple(net.minecraft.world.level.block.Blocks.OAK_LOG),
                kind.equals("acacia")?new net.minecraft.world.level.levelgen.feature.trunkplacers.ForkingTrunkPlacer(4,0,0)
                        :kind.equals("mega_jungle")?new net.minecraft.world.level.levelgen.feature.trunkplacers.MegaJungleTrunkPlacer(6,0,0)
                        :new net.minecraft.world.level.levelgen.feature.trunkplacers.StraightTrunkPlacer(kind.equals("bush")?1:4,0,0),
                net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider.simple(net.minecraft.world.level.block.Blocks.OAK_LEAVES),
                kind.equals("bush")?new net.minecraft.world.level.levelgen.feature.foliageplacers.BushFoliagePlacer(net.minecraft.util.valueproviders.ConstantInt.of(3),net.minecraft.util.valueproviders.ConstantInt.of(0),2)
                        :kind.equals("acacia")?new net.minecraft.world.level.levelgen.feature.foliageplacers.AcaciaFoliagePlacer(net.minecraft.util.valueproviders.ConstantInt.of(2),net.minecraft.util.valueproviders.ConstantInt.of(0))
                        :kind.equals("mega_jungle")?new net.minecraft.world.level.levelgen.feature.foliageplacers.MegaJungleFoliagePlacer(net.minecraft.util.valueproviders.ConstantInt.of(2),net.minecraft.util.valueproviders.ConstantInt.of(0),2)
                        :new net.minecraft.world.level.levelgen.feature.foliageplacers.BlobFoliagePlacer(net.minecraft.util.valueproviders.ConstantInt.of(2),net.minecraft.util.valueproviders.ConstantInt.of(0),3),
                new net.minecraft.world.level.levelgen.feature.featuresize.TwoLayersFeatureSize(1,0,1)).ignoreVines().build();
    }
    /** Exercise actual falling-block ticks beneath the placed floors, not just sturdy-face metadata. */
    private static JsonObject settleGravity(ServerLevel level,List<VillageFootprint.Foot> feet) {
        var cells=new java.util.TreeMap<BlockPos,BlockState>();
        int maximumRun=0;
        for(var foot:feet)for(int depth=1;depth<=12;depth++) {
            var at=foot.position().below(depth);
            if(at.getY()<level.getMinBuildHeight())break;
            var state=level.getBlockState(at);
            if(!(state.getBlock() instanceof net.minecraft.world.level.block.FallingBlock))break;
            cells.put(at,state);
            maximumRun=Math.max(maximumRun,depth);
            if(depth==12)check(!(level.getBlockState(at.below()).getBlock() instanceof net.minecraft.world.level.block.FallingBlock),
                    "Falling support chain exceeds the fixture's twelve-cell budget");
        }
        var changed=new JsonArray();
        for(var at:cells.keySet().stream().sorted(java.util.Comparator.comparingInt(BlockPos::getY)).toList()) {
            var state=level.getBlockState(at);
            state.tick(level,at,net.minecraft.util.RandomSource.create(42));
            if(!level.getBlockState(at).equals(state))changed.add(at.toShortString());
        }
        var row=new JsonObject();row.addProperty("fallingSupportCells",cells.size());row.add("changedByNativeTick",changed);
        row.addProperty("maximumFallingRun",maximumRun);
        if(Boolean.getBoolean("neighbors.probe.groundGravityOnly"))check(!cells.isEmpty(),"Gravity-only fixture has no falling support cells");
        check(changed.isEmpty(),"Native gravity destabilized foundation supports: "+changed);
        return row;
    }
    private static long treeRefusals(net.minecraft.world.level.WorldGenLevel level) {
        return org.sathrek.sky_archipelago.worldgen.benchmark.StructureBenchmarkRecorder
                .report("ground-village",level.getSeed(),List.of(),Map.of()).summary().counters().getOrDefault("skyweaveVillageTreeRefused",0L);
    }
    private static boolean featureControlsEnabled() {
        return !Boolean.getBoolean("neighbors.probe.groundGravityOnly") && !Boolean.getBoolean("neighbors.probe.groundLagoon");
    }
    static void run(MinecraftServer server,Path output) throws Exception {
        var report=new JsonObject();
        try {
            var level=server.overworld();var receipt=server.getWorldPath(LevelResource.ROOT).resolve("ground-village-probe.json");
            if(Files.isRegularFile(receipt)) {
                report=JsonParser.parseString(Files.readString(receipt)).getAsJsonObject();
                for(var value:report.getAsJsonArray("savedBlocks")) {
                    var row=value.getAsJsonObject();var at=point(row);
                    check(level.getBlockState(at).equals(BlockState.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,row.get("state")).getOrThrow()),
                            "Saved production footing changed on reopen: "+at);
                }
                if(report.get("enabled").getAsBoolean()) {
                    var origin=report.getAsJsonArray("origin");var cp=new ChunkPos(Math.floorDiv(origin.get(0).getAsInt(),16),Math.floorDiv(origin.get(2).getAsInt(),16));
                    check(!level.getChunk(cp.x,cp.z).hasData(NaturalStructureInputs.TYPE),"Retired natural input reappeared after save/reopen");
                    for(var value:report.getAsJsonArray("proofChunks")) {
                        var row=value.getAsJsonObject();var real=level.getChunk(row.get("x").getAsInt(),row.get("z").getAsInt());
                        var proof=GroundSupportProofs.existing(level,real);
                        check(proof!=null && proofSignature(proof).equals(row.get("signature").getAsString()),"Final support evidence changed/lost after reopen");
                        for(var entry:proof.entries())check(entry.state()==GroundSupportProofs.State.VERIFIED
                                && GroundSupportProofs.inspect(entry,real)==GroundSupportProofs.State.VERIFIED,"Reopened required foot is not verified");
                    }
                }
                report.addProperty("reopened",true);return;
            }
            var generator=(SkyIslandChunkGenerator)level.getChunkSource().getGenerator();var settings=generator.resolvedSkyIslandSettings();
            var field=((GeologicalGenerator)(Object)generator).skyweave$field(level.getChunkSource().randomState());
            var element=StructurePoolElement.single("minecraft:village/plains/houses/plains_small_house_1").apply(StructureTemplatePool.Projection.RIGID);
            var zero=element.getBoundingBox(server.getStructureManager(),BlockPos.ZERO,Rotation.NONE);
            int width=zero.getXSpan(),depth=zero.getZSpan();check(width<=32 && depth<=32,"Native fixture house exceeds the bounded multi-chunk window: "+zero);
            report.add("templateBounds",new Gson().toJsonTree(List.of(width,zero.getYSpan(),depth)));
            ChunkPos selected=null;int examined=0;boolean shore=Boolean.getBoolean("neighbors.probe.groundLagoon");
            int[] offsets=shore?new int[]{10,-10,9,-9,8,-8,7,-7,6,-6,5,-5,4,-4,3,-3,2,-2,1,-1,0}:new int[]{-2,-1,0,1,2};
            outer:for(int cx=shore?3:8;cx<=(shore?3:16);cx++)for(int cz=shore?6:-16;cz<=(shore?6:-8);cz++) {
                for(var island:((ClusterField)(Object)field).skyweave$members(cx,cz,settings)) {
                    if(island.family()!=IslandFamily.ANCHOR_PLATEAU || island.archetype().waterBased()!=shore)continue;
                    for(int dx:offsets)for(int dz:offsets) {
                        var cp=new ChunkPos(Math.floorDiv(island.centerX()+island.maxRadius()*dx/(shore?10:5),16),Math.floorDiv(island.centerZ()+island.maxRadius()*dz/(shore?10:5),16));
                        if(++examined>256)break outer;
                        var snapshots=new HashMap<ChunkPos,org.sathrek.sky_archipelago.worldgen.generator.terrain.SkyIslandChunkTerrainSnapshot>();
                        int low=Integer.MAX_VALUE,high=Integer.MIN_VALUE;boolean valid=true;
                        for(int x=4;x<4+width;x++)for(int z=4;z<4+depth;z++) {
                            int bx=cp.getMinBlockX()+x,bz=cp.getMinBlockZ()+z;
                            var snapshot=snapshots.computeIfAbsent(new ChunkPos(Math.floorDiv(bx,16),Math.floorDiv(bz,16)),p->field.sampleChunkTerrainSnapshot(p,settings));
                            var column=snapshot.columnDataAt(bx,bz);
                            if(column.segments().isEmpty() || !island.equals(column.dominantDescriptor()) || column.river().affected()
                                    || column.waterArchetype().containsWaterAt(column.segments().getFirst().topY()+1)
                                    || ((InteriorField)(Object)field).skyweave$isFringe(island,cp.getMinBlockX()+x,cp.getMinBlockZ()+z,settings)) { valid=false;break; }
                            int top=column.segments().getFirst().topY();low=Math.min(low,top);high=Math.max(high,top);
                        }
                        if(valid && high-low<=1) { selected=cp;report.add("terrainHost",new Gson().toJsonTree(island));break outer; }
                    }
                }
            }
            check(selected!=null,"No bounded real-terrain house window found");
            var chunk=level.getChunkSource().getChunk(selected.x,selected.z,ChunkStatus.CARVERS,true);
            check(chunk!=null && chunk.getPersistedStatus()==ChunkStatus.CARVERS,"Fixture must start before FEATURES");
            var neighbors=new HashMap<ChunkPos,ChunkAccess>();
            int maxChunkX=Math.floorDiv(selected.getMinBlockX()+3+width,16),maxChunkZ=Math.floorDiv(selected.getMinBlockZ()+3+depth,16);
            for(int x=selected.x-1;x<=maxChunkX+1;x++)for(int z=selected.z-1;z<=maxChunkZ+1;z++)
                neighbors.put(new ChunkPos(x,z),level.getChunkSource().getChunk(x,z,ChunkStatus.CARVERS,true));
            int highest=Integer.MIN_VALUE,lowest=Integer.MAX_VALUE;
            for(int x=4;x<4+width;x++)for(int z=4;z<4+depth;z++) {
                int top=chunk.getMaxBuildHeight()-1;var at=new BlockPos.MutableBlockPos(selected.getMinBlockX()+x,top,selected.getMinBlockZ()+z);
                var actual=neighbors.get(new ChunkPos(at));
                while(top>=chunk.getMinBuildHeight() && actual.getBlockState(at.setY(top)).isAir())top--;
                check(top>=chunk.getMinBuildHeight() && NaturalMaterials.skin(actual.getBlockState(at.setY(top))),"Physical natural roof unavailable");
                highest=Math.max(highest,top);lowest=Math.min(lowest,top);
            }
            check(highest-lowest<=1,"Actual CARVERS roof differs from bounded metadata window");
            var origin=new BlockPos(selected.getMinBlockX()+4,highest+3,selected.getMinBlockZ()+4);
            var piece=new PoolElementStructurePiece(server.getStructureManager(),element,origin,element.getGroundLevelDelta(),Rotation.NONE,
                    element.getBoundingBox(server.getStructureManager(),origin,Rotation.NONE),LiquidSettings.APPLY_WATERLOGGING);
            var first=piece.getBoundingBox();var c=first.getCenter();var reference=new BlockPos(c.getX(),first.minY(),c.getZ());
            var before=StructureTerrainView.capture(level.getSeed(),level.registryAccess(),neighbors.values());
            var feet=VillageFootprint.compile(piece,0,reference,server.getStructureManager(),before,new VillageFootprint.Budget(),null);
            check(feet.qualified() && !feet.feet().isEmpty(),"Native diagnostic house has no qualified feet");
            int missingBefore=0;for(var foot:feet.feet())if(!neighbors.get(new ChunkPos(foot.position())).getBlockState(foot.position().below())
                    .isFaceSturdy(net.minecraft.world.level.EmptyBlockGetter.INSTANCE,foot.position().below(),Direction.UP))missingBefore++;
            check(missingBefore==feet.feet().size(),"Fixture did not reproduce unsupported native house");
            var watch=new HashMap<BlockPos,BlockState>();for(var foot:feet.feet())watch.put(foot.position(),foot.expected());
            CHANGES.clear();CHANGE_COUNT.set(0);watched=Map.copyOf(watch);
            var structure=server.registryAccess().registryOrThrow(Registries.STRUCTURE).get(ResourceLocation.withDefaultNamespace("village_plains"));
            var start=new StructureStart(structure,selected,0,new PiecesContainer(List.of(piece)));
            chunk.setStartForStructure(structure,start);chunk.setUnsaved(true);
            for(var neighbor:neighbors.values()) {
                var cp=neighbor.getPos();
                if(start.getBoundingBox().intersects(cp.getMinBlockX(),cp.getMinBlockZ(),cp.getMaxBlockX(),cp.getMaxBlockZ())) {
                    neighbor.addReferenceForStructure(structure,selected.toLong());neighbor.setUnsaved(true);
                }
            }
            var finished=level.getChunk(selected.x,selected.z);int contacts=0;var saved=new JsonArray();
            report.add("nativeGravity",settleGravity(level,feet.feet()));
            report.add("origin",new Gson().toJsonTree(List.of(origin.getX(),origin.getY(),origin.getZ())));
            for(var foot:feet.feet()) {
                var at=foot.position();level.getChunk(Math.floorDiv(at.getX(),16),Math.floorDiv(at.getZ(),16));
                check(level.getBlockState(at).equals(foot.expected()),"Actual native FEATURES output did not match predicted foot: "+at
                        +" expected="+foot.expected()+" actual="+level.getBlockState(at));
                if(level.getBlockState(at.below()).isFaceSturdy(level,at.below(),Direction.UP))contacts++;
                record(saved,at,level.getBlockState(at));record(saved,at.below(),level.getBlockState(at.below()));
            }
            boolean enabled=OptionalIntegrations.enabled(Feature.GROUND_SUPPORT);int grading=0,physicalGrading=0;
            report.addProperty("feet",feet.feet().size());report.addProperty("contactsAfter",contacts);report.add("savedBlocks",saved);
            var counters=org.sathrek.sky_archipelago.worldgen.benchmark.StructureBenchmarkRecorder
                    .report("ground-village",level.getSeed(),List.of(),Map.of()).summary().counters();
            report.add("counters",new Gson().toJsonTree(counters));
            if(enabled) {
                check(counters.getOrDefault("skyweaveVillageUnknownFeatureRefusal",0L)==0L,"Known native fixture fell back to unknown-layout feature refusal");
                var journal=PREPARED.get(finished.getPos());
                check(journal!=null,"Production feature hook did not bind grading intent before native placement");
                check(!finished.hasData(NaturalStructureInputs.TYPE),"Natural input survived the native LIGHT barrier");
                report.addProperty("sourceRetired",true);
                var proofChunks=new JsonArray();report.add("proofChunks",proofChunks);
                var proofPositions=new HashSet<BlockPos>();
                for(var cp:feet.feet().stream().map(f->new ChunkPos(f.position())).distinct().toList()) {
                    var real=level.getChunk(cp.x,cp.z);var proof=GroundSupportProofs.existing(level,real);
                    check(proof!=null,"Native LIGHT produced no local support proof");
                    for(var entry:proof.entries()) {
                        check(entry.state()==GroundSupportProofs.State.VERIFIED && GroundSupportProofs.inspect(entry,real)==GroundSupportProofs.State.VERIFIED,
                                "Native final support verification failed: "+entry.foot()+" "+entry.state());
                        proofPositions.add(entry.foot().position());
                    }
                    var row=new JsonObject();row.addProperty("x",cp.x);row.addProperty("z",cp.z);row.addProperty("signature",proofSignature(proof));proofChunks.add(row);
                }
                for(var foot:feet.feet())check(proofPositions.contains(foot.position()),"Final support evidence omitted an authored foot");
                report.addProperty("verifiedFootPositions",proofPositions.size());
                report.addProperty("proofRefusalControls",proofControls(level,finished));
                report.addProperty("journalSignature",signature(journal));
                grading=journal.cells().size();if(!shore)check(grading>0,"Native Beardifier produced no physical grading");
                var gradingRows=new JsonArray();report.add("gradingDiagnostic",gradingRows);
                for(var cell:journal.cells()) {
                    var actual=level.getBlockState(cell.position());record(saved,cell.position(),actual);
                    if(actual.equals(cell.target()) && !actual.equals(cell.original()))physicalGrading++;
                    var row=new JsonObject();row.addProperty("position",cell.position().toShortString());
                    row.addProperty("original",cell.original().toString());row.addProperty("target",cell.target().toString());
                    row.addProperty("actual",actual.toString());gradingRows.add(row);
                }
                report.addProperty("physicalGradingCells",physicalGrading);
                check(contacts==feet.feet().size(),"Production support left "+(feet.feet().size()-contacts)+" unsupported feet");
                if(!shore)check(physicalGrading>0,"Retained grading intent had no surviving physical output");
                report.addProperty("gradingRequiredByFixture",!shore);
                report.addProperty("preflightAndJournalControls",PREPARATION_CONTROLS.get(finished.getPos()));
                boolean retiredRefused=false;try { NaturalStructureInputs.bindGrading(level,finished,journal.layout(),journal.cells()); }
                catch(StructureTerrainView.Unavailable expected) { retiredRefused=true; }
                check(retiredRefused && !finished.hasData(NaturalStructureInputs.TYPE),"Late grading recreated a retired source");
                if(featureControlsEnabled() && !UNRELATED_FEATURE_TESTED.get()) {
                    // A one-chunk house need not naturally advance decoration outside its protected halo.
                    level.getChunk(selected.x+3,selected.z);
                    report.addProperty("additionalFeatureControlChunk",new ChunkPos(selected.x+3,selected.z).toString());
                }
                if(featureControlsEnabled())
                    check(BLOCKED_FEATURE_TESTED.get() && UNRELATED_FEATURE_TESTED.get() && NEIGHBOR_FEATURE_TESTED.get(),"Native feature reservation controls not exercised");
                report.addProperty("gravityOnly",Boolean.getBoolean("neighbors.probe.groundGravityOnly"));
                report.add("nativeFeatureControls",FEATURE_CONTROLS.deepCopy());
            } else check(contacts<feet.feet().size(),"Disabled control unexpectedly repaired every native footing");
            check(finished.getStartForStructure(structure)!=null && finished.getStartForStructure(structure).getPieces().size()==1,"Native start did not persist");
            report.addProperty("scope","Controlled native one-piece start on real island; natural candidacy, whole village and late-neighbor protection unqualified");
            report.addProperty("enabled",enabled);report.addProperty("metadataWindows",examined);report.addProperty("feet",feet.feet().size());
            report.addProperty("unsupportedBefore",missingBefore);report.addProperty("contactsAfter",contacts);report.addProperty("gradingCells",grading);
            report.addProperty("physicalGradingCells",physicalGrading);
            report.add("origin",new Gson().toJsonTree(List.of(origin.getX(),origin.getY(),origin.getZ())));report.add("savedBlocks",saved);
            report.addProperty("reopened",false);report.addProperty("complete",true);Files.writeString(receipt,report.toString());
        } catch(Throwable failure) { report.addProperty("complete",false);report.addProperty("failure",failure.toString());throw failure; }
        finally {
            watched=Map.of();report.add("changedFootWrites",new Gson().toJsonTree(List.copyOf(CHANGES)));
            Files.writeString(output,new GsonBuilder().setPrettyPrinting().create().toJson(report)+"\n");
        }
    }
    private static BlockPos point(JsonObject row) { return new BlockPos(row.get("x").getAsInt(),row.get("y").getAsInt(),row.get("z").getAsInt()); }
    private static String proofSignature(GroundSupportProofs.Proof proof) throws java.security.NoSuchAlgorithmException {
        return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(proof.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
    private static int proofControls(net.minecraft.server.level.ServerLevel level,ChunkAccess real) {
        var tags=real.writeAttachmentsToNBT(level.registryAccess());var key="skyweave_compat:ground_support_proofs";int count=0;
        check(tags!=null && tags.contains(key,net.minecraft.nbt.Tag.TAG_COMPOUND),"Final support attachment was not serialized");
        for(int mode=0;mode<13;mode++) {
            var bad=tags.copy();var root=bad.getCompound(key);var entries=root.getList("entries",net.minecraft.nbt.Tag.TAG_COMPOUND);
            var first=entries.getCompound(0);
            switch(mode) {
                case 0 -> root.putInt("schema",Integer.MAX_VALUE);
                case 1 -> root.putInt("x",real.getPos().x+1);
                case 2 -> root.putString("generation","0".repeat(64));
                case 3 -> root.putString("dimension","minecraft:the_nether");
                case 4 -> root.remove("seed");
                case 5 -> first.putString("state","UNKNOWN");
                case 6 -> entries.add(first.copy());
                case 7 -> first.getList("required",net.minecraft.nbt.Tag.TAG_COMPOUND).removeLast();
                case 8 -> first.getList("required",net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(1).putLong("position",
                        BlockPos.of(first.getList("required",net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(1).getLong("position")).offset(16,0,0).asLong());
                case 9 -> first.putString("unknown","unsupported");
                case 10 -> root.putString("height","896");
                case 11 -> first.getList("required",net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(1).put("state",
                        BlockState.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE,net.minecraft.world.level.block.Blocks.WATER.defaultBlockState()).getOrThrow());
                case 12 -> { var over=new net.minecraft.nbt.ListTag();for(int i=0;i<=VillageFootprint.Budget.MAX_FEET;i++)over.add(first);root.put("entries",over); }
                default -> throw new AssertionError();
            }
            var detached=new net.minecraft.world.level.chunk.ProtoChunk(real.getPos(),net.minecraft.world.level.chunk.UpgradeData.EMPTY,level,
                    level.registryAccess().registryOrThrow(Registries.BIOME),null);
            detached.readAttachmentsFromNBT(level.registryAccess(),bad);
            check(GroundSupportProofs.existing(level,detached)==null,"Malformed/mismatched support evidence became authoritative: "+mode);count++;
        }
        var detached=new net.minecraft.world.level.chunk.ProtoChunk(real.getPos(),net.minecraft.world.level.chunk.UpgradeData.EMPTY,level,
                level.registryAccess().registryOrThrow(Registries.BIOME),null);
        detached.readAttachmentsFromNBT(level.registryAccess(),tags);
        var original=GroundSupportProofs.existing(level,detached);check(original!=null,"Native proof copy unavailable");
        for(var entry:original.entries())for(var expected:entry.required())detached.setBlockState(expected.position(),expected.state(),false);
        boolean early=false;try { GroundSupportProofs.verify(level,detached); }catch(IllegalStateException expected) { early=true; }
        check(early,"Final support verification bypassed the native feature barrier");count++;
        var foot=original.entries().getFirst().foot();detached.setBlockState(foot.position(),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),false);
        detached.setPersistedStatus(ChunkStatus.INITIALIZE_LIGHT);
        check(GroundSupportProofs.inspect(original.entries().getFirst(),detached)==GroundSupportProofs.State.INVALIDATED,"Changed required foot retained verification credit");count++;
        GroundSupportProofs.verify(level,detached);
        check(GroundSupportProofs.existing(level,detached).entries().getFirst().state()==GroundSupportProofs.State.INVALIDATED,"Final failure was not retained");count++;
        detached.setPersistedStatus(ChunkStatus.CARVERS);detached.setBlockState(foot.position(),foot.expected(),false);
        detached.setPersistedStatus(ChunkStatus.INITIALIZE_LIGHT);GroundSupportProofs.verify(level,detached);
        check(GroundSupportProofs.existing(level,detached).entries().getFirst().state()==GroundSupportProofs.State.INVALIDATED,"Restored blocks promoted a stored failure");count++;
        var pending=tags.copy();for(var value:pending.getCompound(key).getList("entries",net.minecraft.nbt.Tag.TAG_COMPOUND))((net.minecraft.nbt.CompoundTag)value).putString("state","PENDING");
        detached.readAttachmentsFromNBT(level.registryAccess(),pending);
        check(GroundSupportProofs.existing(level,detached).entries().stream().allMatch(e->e.state()==GroundSupportProofs.State.PENDING),"Pending proof control did not replace its input");
        GroundSupportProofs.verify(level,detached);
        check(GroundSupportProofs.existing(level,detached).entries().stream().allMatch(e->e.state()==GroundSupportProofs.State.INVALIDATED),"Missing natural intent promoted pending support");count++;
        return count;
    }
    private static String signature(NaturalStructureInputs.Journal journal) throws java.security.NoSuchAlgorithmException {
        var text=new StringBuilder(journal.layout());
        for(var cell:journal.cells())text.append('\n').append(cell.position().asLong()).append('|').append(cell.original()).append('|').append(cell.target());
        return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(text.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
    private static int controls(net.minecraft.server.level.ServerLevel level,ChunkAccess real,NaturalStructureInputs.Journal journal) throws Exception {
        var detached=new net.minecraft.world.level.chunk.ProtoChunk(real.getPos(),net.minecraft.world.level.chunk.UpgradeData.EMPTY,level,
                level.registryAccess().registryOrThrow(Registries.BIOME),null);
        for(var cell:journal.cells())detached.setBlockState(cell.position(),cell.target(),false);
        check(VillageGrading.canApply(journal.cells(),detached),"Idempotent target patch refused");
        var picked=journal.cells().stream().filter(c->c.original().isAir()).findFirst().orElseThrow();int count=0;
        for(var state:List.of(net.minecraft.world.level.block.Blocks.BRICKS.defaultBlockState(),net.minecraft.world.level.block.Blocks.WATER.defaultBlockState())) {
            detached.setBlockState(picked.position(),state,false);
            check(!VillageGrading.canApply(journal.cells(),detached),"Foreign solid/fluid overwrite authorized");
            for(var cell:journal.cells())if(!cell.position().equals(picked.position()))check(detached.getBlockState(cell.position()).equals(cell.target()),"Preflight wrote a partial patch");
            count++;
        }
        detached.setBlockState(picked.position(),net.minecraft.world.level.block.Blocks.SHORT_GRASS.defaultBlockState(),false);
        check(VillageGrading.canApply(journal.cells(),detached) && detached.getBlockState(picked.position()).is(net.minecraft.world.level.block.Blocks.SHORT_GRASS),
                "Replaceable decoration preflight mutated input or refused");count++;
        for(var patch:List.of(List.of(picked,picked),
                List.of(new VillageGrading.Cell(picked.position().atY(level.getMaxBuildHeight()),picked.original(),picked.target())),
                List.of(new VillageGrading.Cell(picked.position().offset(16,0,0),picked.original(),picked.target())))) {
            boolean refused=false;try { VillageGrading.canApply(patch,detached); }catch(IllegalArgumentException expected) { refused=true; }
            check(refused,"Malformed grading patch accepted");count++;
        }
        boolean unbound=false;try { VillageGrading.apply(level,journal.cells(),detached); }catch(IllegalStateException expected) { unbound=true; }
        check(unbound,"Unbound grading wrote without a retained source");count++;
        var before=signature(journal);boolean changed=false;
        try { NaturalStructureInputs.bindGrading(level,real,"0".repeat(64),journal.cells()); }catch(IllegalStateException expected) { changed=true; }
        check(changed,"Changed layout identity accepted");count++;
        var replacement=new ArrayList<>(journal.cells());var first=replacement.getFirst();
        replacement.set(0,new VillageGrading.Cell(first.position(),first.original(),first.target().is(net.minecraft.world.level.block.Blocks.STONE)
                ?net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState():net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()));
        changed=false;try { NaturalStructureInputs.bindGrading(level,real,journal.layout(),replacement); }catch(IllegalStateException expected) { changed=true; }
        check(changed && signature(NaturalStructureInputs.existing(level,real).grading()).equals(before),"Changed grading intent replaced retained authority");count++;
        return count;
    }
    private static void record(JsonArray rows,BlockPos at,BlockState state) {
        var row=new JsonObject();row.addProperty("x",at.getX());row.addProperty("y",at.getY());row.addProperty("z",at.getZ());
        row.add("state",BlockState.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE,state).getOrThrow());rows.add(row);
    }
}
