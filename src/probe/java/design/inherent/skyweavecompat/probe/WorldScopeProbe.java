package design.inherent.skyweavecompat.probe;

import com.AdamNew.dynamicwaters.HydrologyManager;
import com.AdamNew.dynamicwaters.terrain.CoarseHeightField;
import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import de.bluecolored.bluemap.common.config.MapConfig;
import de.bluecolored.bluemap.common.config.ConfigManager;
import design.inherent.skyweavecompat.climate.IslandClimate;
import design.inherent.skyweavecompat.placement.VillageAnchors;
import design.inherent.skyweavecompat.terrain.GeologicalGenerator;
import design.inherent.skyweavecompat.terrain.structure.VillageComposition;
import design.inherent.skyweavecompat.water.OwnedWaterHeights;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;

/** Finite native consumer controls; no client, terrain writes, or chunk requests. */
public final class WorldScopeProbe {
    private static final Set<ChunkGenerator> WATCHED = Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));
    private static final Map<ChunkGenerator, int[]> ENVELOPES = Collections.synchronizedMap(new IdentityHashMap<>());
    private static final Set<String> HEIGHT_THREADS = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private MinecraftServer pending;
    private CompletableFuture<Void> reload;
    private JsonObject report, reloadReport;
    private Path contract;
    private int phase;
    private boolean firstAccepted;
    private JsonObject retained;

    public WorldScopeProbe() {
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> pending = event.getServer());
        NeoForge.EVENT_BUS.addListener(this::tick);
    }

    public static void envelope(ChunkGenerator generator, LevelHeightAccessor accessor) {
        if (WATCHED.contains(generator)) ENVELOPES.put(generator, new int[]{accessor.getMinBuildHeight(), accessor.getMaxBuildHeight()});
    }
    public static void height(ChunkGenerator generator) {
        if (WATCHED.contains(generator)) HEIGHT_THREADS.add(Thread.currentThread().getName());
    }

    private void tick(ServerTickEvent.Post event) {
        var server = event.getServer();
        try {
            if (pending != null) {
                pending = null;
                report = new JsonObject(); report.addProperty("complete", false);
                report.addProperty("case", System.getProperty("neighbors.probe.worldScope"));
                int loaded = server.overworld().getChunkSource().getLoadedChunksCount();
                report.add("pairings", pairings(server));
                report.add("storagePaths", storagePaths(server));
                report.add("selectedPacks",new Gson().toJsonTree(server.getPackRepository().getSelectedIds()));
                var ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
                var ownedJson = JsonParser.parseString(Files.readString(Path.of("scope-owned-generator.json")));
                var owned = (SkyIslandChunkGenerator) ChunkGenerator.CODEC.parse(ops, ownedJson).getOrThrow();
                var random = RandomState.create(owned.generatorSettings().value(), server.registryAccess().lookupOrThrow(Registries.NOISE), 42);
                report.add("S1", villages(server, owned, random));
                report.add("W2", water(server, owned, random, ops));
                report.add("M1", mapBounds(server));
                report.addProperty("loadedBefore", loaded);
                report.addProperty("loadedAfter", server.overworld().getChunkSource().getLoadedChunksCount());
                contract = Path.of("world/datapacks/scope-live/data/skyvillages/village_composition/skyvillage.json");
                reloadReport = new JsonObject(); report.add("L1", reloadReport);
                retained = VillageComposition.generationInputs().deepCopy();
                contract(2); reload = server.reloadResources(server.getPackRepository().getSelectedIds()); phase = 1;
                return;
            }
            if (reload == null || !reload.isDone()) return;
            Throwable failure = null;
            try { reload.join(); } catch (java.util.concurrent.CompletionException error) { failure = error.getCause(); }
            if (phase == 1) {
                firstAccepted = failure == null;
                reloadReport.addProperty("changedAccepted", firstAccepted);
                reloadReport.addProperty("changedFailure", cause(failure));
                reloadReport.addProperty("refusalRetainedInputs", firstAccepted || retained.equals(VillageComposition.generationInputs()));
                contract(firstAccepted ? 2 : 1);
            } else if (phase == 2) {
                reloadReport.addProperty("unchangedAccepted", failure == null);
                retained = VillageComposition.generationInputs().deepCopy();
                Files.writeString(contract, "{\"schema\":999,\"roles\":{}}");
            } else if (phase == 3) {
                reloadReport.addProperty("malformedRefused", failure != null);
                reloadReport.addProperty("malformedRetainedInputs", retained.equals(VillageComposition.generationInputs()));
                contract(1);
            } else {
                reloadReport.addProperty("restoredAccepted", failure == null);
                report.addProperty("complete", true);
                Files.writeString(Path.of(System.getProperty("neighbors.probe.output")), new GsonBuilder().setPrettyPrinting().create().toJson(report));
                reload = null; WATCHED.clear(); ENVELOPES.clear(); server.halt(false); return;
            }
            phase++; reload = server.reloadResources(server.getPackRepository().getSelectedIds());
        } catch (Throwable failure) {
            if (report == null) report = new JsonObject();
            report.addProperty("complete", false); report.addProperty("error", cause(failure));
            try { Files.writeString(Path.of(System.getProperty("neighbors.probe.output")), report.toString()); }
            catch (Exception save) { failure.addSuppressed(save); }
            failure.printStackTrace(); WATCHED.clear(); ENVELOPES.clear(); server.halt(false);
        }
    }

    private static String cause(Throwable error) {
        var text = new StringBuilder();
        for (int i=0; error!=null && i<12; i++,error=error.getCause()) text.append(error).append('\n');
        return text.toString();
    }

    /** The real DimensionType API initializes registries; positive path controls belong in this bootstrapped fixture. */
    private static JsonObject storagePaths(MinecraftServer server) throws Exception {
        var result=new JsonObject();
        java.lang.reflect.Method method;
        try {method=design.inherent.skyweavecompat.terrain.GenerationGuard.class.getDeclaredMethod("dimensionPath",Path.class,ResourceKey.class);}
        catch(NoSuchMethodException baseline) {result.addProperty("implemented",false);return result;}
        method.setAccessible(true);var root=server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        for(var entry:Map.of("minecraft:overworld","", "minecraft:the_nether","DIM-1", "minecraft:the_end","DIM1",
                "example:nested/islands","dimensions/example/nested/islands").entrySet()) {
            var key=ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(entry.getKey()));
            var actual=(Path)method.invoke(null,root,key);
            result.addProperty(entry.getKey(),actual.equals(root.resolve(entry.getValue())));
        }
        return result;
    }

    private static JsonArray pairings(MinecraftServer server) {
        var result = new JsonArray();
        var levels=new ArrayList<net.minecraft.server.level.ServerLevel>();server.getAllLevels().forEach(levels::add);
        levels.sort(Comparator.comparing(level->level.dimension().location()));
        for (var level : levels) {
            var generator = level.getChunkSource().getGenerator();
            boolean owned = generator instanceof GeologicalGenerator geology && geology.skyweave$isEnabled();
            var row = new JsonObject(); row.addProperty("dimension", level.dimension().location().toString());
            row.addProperty("type", level.dimensionTypeRegistration().getRegisteredName()); row.addProperty("owned", owned);
            row.addProperty("climateApplies", IslandClimate.applies(level));
            var pos = new BlockPos(0,200,0); row.addProperty("temperatureY", IslandClimate.temperaturePosition(level,pos).getY());
            if (owned) {
                try {
                    var guard = design.inherent.skyweavecompat.terrain.GenerationGuard.class;
                    java.lang.reflect.Method method;
                    try { method = guard.getMethod("fingerprint", net.minecraft.server.level.ServerLevel.class); }
                    catch (NoSuchMethodException old) { method = guard.getMethod("fingerprint", MinecraftServer.class); }
                    var argument = method.getParameterTypes()[0] == MinecraftServer.class ? server : level;
                    var identity=method.invoke(null,argument);row.addProperty("verifiedIdentity", identity instanceof String);
                    if(identity instanceof String text)row.addProperty("generationIdentity",text);
                } catch (ReflectiveOperationException error) { row.addProperty("verifiedIdentity",false); row.addProperty("identityFailure",cause(error)); }
            }
            result.add(row);
        }
        return result;
    }

    private static final class Surface extends NoiseBasedChunkGenerator {
        private final int surface;
        Surface(FixedBiomeSource biome, Holder<NoiseGeneratorSettings> settings, int surface) { super(biome, settings); this.surface = surface; }
        @Override public int getBaseHeight(int x,int z,Heightmap.Types type,LevelHeightAccessor level,RandomState random) { return surface; }
        @Override public NoiseColumn getBaseColumn(int x,int z,LevelHeightAccessor level,RandomState random) {
            var states = new BlockState[level.getHeight()]; Arrays.fill(states,Blocks.AIR.defaultBlockState());
            Arrays.fill(states,0,Math.clamp(surface-level.getMinBuildHeight(),0,states.length),Blocks.STONE.defaultBlockState());
            return new NoiseColumn(level.getMinBuildHeight(),states);
        }
    }

    private static JsonObject villages(MinecraftServer server,SkyIslandChunkGenerator owned,RandomState random) throws Exception {
        var result = new JsonObject(); var access = server.registryAccess(); var ops = RegistryOps.create(JsonOps.INSTANCE,access);
        var actual = (JigsawStructure)access.registryOrThrow(Registries.STRUCTURE).get(ResourceLocation.parse("skyvillages:skyvillage"));
        var reference = (JigsawStructure)Structure.DIRECT_CODEC.parse(ops, JsonParser.parseString(Files.readString(Path.of("scope-native-skyvillage.json")))).getOrThrow();
        result.add("loadedDefinition",Structure.DIRECT_CODEC.encodeStart(ops,actual).getOrThrow());
        result.addProperty("resourceOwner",server.getResourceManager().getResource(ResourceLocation.parse("skyvillages:worldgen/structure/skyvillage.json")).orElseThrow().sourcePackId());
        var settings = access.registryOrThrow(Registries.NOISE_SETTINGS).getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD);
        var biome = new FixedBiomeSource(access.registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.PLAINS));
        var rows = new JsonArray(); int differences=0;
        for (int surface : new int[]{64,200,260}) for(long seed:new long[]{42,77}) {
            var generator = new Surface(biome,settings,surface);
            var current = start(server,actual,generator,random,seed,new ChunkPos(0,0));
            var nativeValue = start(server,reference,generator,random,seed,new ChunkPos(0,0));
            var row = new JsonObject(); row.addProperty("surface",surface);row.addProperty("seed",seed);
            row.add("actual",current);row.add("reference",nativeValue);rows.add(row);
            if(!current.equals(nativeValue))differences++;
        }
        result.add("foreign",rows); result.addProperty("foreignDifferences",differences);
        var anchors=((VillageAnchors)(Object)((GeologicalGenerator)(Object)owned).skyweave$field(random))
                .skyweave$anchorsNear(0,0,owned.resolvedSkyIslandSettings());
        var chunk=anchors.isEmpty()?new ChunkPos(0,0):new ChunkPos(new BlockPos(anchors.getFirst().x(),0,anchors.getFirst().z()));
        var ownedRows=new JsonArray();
        for(long seed:new long[]{42,77})ownedRows.add(start(server,actual,owned,random,seed,chunk));
        result.add("owned",ownedRows);result.addProperty("ownedAnchorCount",anchors.size());
        return result;
    }

    private static JsonObject start(MinecraftServer server,JigsawStructure structure,ChunkGenerator generator,RandomState random,long seed,ChunkPos chunk) throws Exception {
        var context=new Structure.GenerationContext(server.registryAccess(),generator,generator.getBiomeSource(),random,
                server.getStructureManager(),seed,chunk,LevelHeightAccessor.create(generator.getMinY(),generator.getGenDepth()),biome->true);
        var stub=structure.findGenerationPoint(context);var row=new JsonObject();row.addProperty("present",stub.isPresent());
        if(stub.isPresent()) {
            var value=stub.get();row.addProperty("x",value.position().getX());row.addProperty("y",value.position().getY());row.addProperty("z",value.position().getZ());
            var pieces=value.getPiecesBuilder().build().pieces();row.addProperty("pieces",pieces.size());
            var serializer=StructurePieceSerializationContext.fromLevel(server.overworld());var text=new StringBuilder();
            for(var piece:pieces)text.append(piece.createTag(serializer)).append('\n');
            row.addProperty("pieceDigest",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        }
        row.addProperty("nextRandom",context.random().nextLong());return row;
    }

    private static JsonObject water(MinecraftServer server,SkyIslandChunkGenerator owned,RandomState random,RegistryOps<JsonElement> ops) throws Exception {
        var flatJson=JsonParser.parseString("{\"type\":\"minecraft:flat\",\"settings\":{\"biome\":\"minecraft:plains\",\"layers\":[{\"height\":1,\"block\":\"minecraft:bedrock\"},{\"height\":2,\"block\":\"minecraft:dirt\"},{\"height\":1,\"block\":\"minecraft:grass_block\"}],\"lakes\":false,\"features\":false,\"structure_overrides\":[]}}");
        var foreign=ChunkGenerator.CODEC.parse(ops,flatJson).getOrThrow();WATCHED.add(owned);WATCHED.add(foreign);
        var result=new JsonObject();
        CoarseHeightField.reset();int expectedForeign=CoarseHeightField.sample(0,0,foreign,random);
        CoarseHeightField.reset();int expectedOwned=CoarseHeightField.sample(0,0,owned,random);
        int foreignAfterOwned=CoarseHeightField.sample(0,0,foreign,random);
        CoarseHeightField.reset();int foreignBefore=CoarseHeightField.sample(0,0,foreign,random);
        int ownedAfterForeign=CoarseHeightField.sample(0,0,owned,random);
        result.addProperty("foreignCold",expectedForeign);result.addProperty("ownedCold",expectedOwned);
        result.addProperty("foreignAfterOwned",foreignAfterOwned);result.addProperty("ownedAfterForeign",ownedAfterForeign);
        result.addProperty("scalarIsolated",foreignAfterOwned==expectedForeign&&foreignBefore==expectedForeign&&ownedAfterForeign==expectedOwned);
        CoarseHeightField.reset();var window=new float[128*128];
        CoarseHeightField.fillWindow(-64,-64,128,128,window,owned,random);
        var foreignWindow=new float[128*128];CoarseHeightField.fillWindow(-64,-64,128,128,foreignWindow,foreign,random);
        int bad=0;for(float h:foreignWindow)if(h!=expectedForeign)bad++;
        result.addProperty("foreignWindowWrongCells",bad);result.add("heightThreads",new Gson().toJsonTree(new TreeSet<>(HEIGHT_THREADS)));
        try {
            Class.forName("design.inherent.skyweavecompat.water.OwnedWaterHeights",false,WorldScopeProbe.class.getClassLoader());
            result.add("cacheControls",cacheControls(server,owned,foreign,random));
        } catch(ClassNotFoundException baseline) {
            var absent=new JsonObject();absent.addProperty("implemented",false);result.add("cacheControls",absent);
        }
        var global=HydrologyManager.getGlobalGenerator();var field=HydrologyManager.class.getDeclaredField("globalRandomState");field.setAccessible(true);
        var globalRandom=(RandomState)field.get(null);
        try {
            HydrologyManager.setGlobalGenerator(foreign,random);
            var nativeEnvelope=HydrologyManager.getDummyAccessor();int[] expected={nativeEnvelope.getMinBuildHeight(),nativeEnvelope.getMaxBuildHeight()};
            HydrologyManager.setGlobalGenerator(owned,random);
            HydrologyManager.lowlandCut(0,0,64,63,foreign,random);
            HydrologyManager.lowlandCut(0,0,256,63,owned,random);
            result.add("nativeEnvelope",new Gson().toJsonTree(expected));result.add("foreignEnvelope",new Gson().toJsonTree(ENVELOPES.get(foreign)));
            result.add("ownedEnvelope",new Gson().toJsonTree(ENVELOPES.get(owned)));
            result.addProperty("envelopesIsolated",Arrays.equals(expected,ENVELOPES.get(foreign))
                    &&Arrays.equals(new int[]{owned.getMinY(),owned.getMinY()+owned.getGenDepth()},ENVELOPES.get(owned)));
        } finally {HydrologyManager.setGlobalGenerator(global,globalRandom);CoarseHeightField.reset();WATCHED.clear();}
        return result;
    }

    private static JsonObject cacheControls(MinecraftServer server,SkyIslandChunkGenerator owned,ChunkGenerator foreign,RandomState random) throws Exception {
        var result=new JsonObject();
        var block=CoarseHeightField.class.getDeclaredMethod("block",int.class,int.class,ChunkGenerator.class,RandomState.class);
        block.setAccessible(true);CoarseHeightField.reset();
        var first=(short[])block.invoke(null,0,0,owned,random);
        boolean hit=true;for(int i=0;i<100;i++)hit&=first==block.invoke(null,0,0,owned,random);
        result.addProperty("warmHitIdentity",hit);
        var states=new ArrayList<RandomState>();
        for(int seed=77;seed<82;seed++)states.add(RandomState.create(owned.generatorSettings().value(),
                server.registryAccess().lookupOrThrow(Registries.NOISE),seed));
        var other=(short[])block.invoke(null,0,0,owned,states.getFirst());
        result.addProperty("randomStateIsolated",other!=first&&first==block.invoke(null,0,0,owned,random));
        for(var state:states)block.invoke(null,0,0,owned,state);
        var recomputed=(short[])block.invoke(null,0,0,owned,random);
        result.addProperty("evictedEqualAndRecached",recomputed!=first&&Arrays.equals(first,recomputed)
                &&recomputed==block.invoke(null,0,0,owned,random));
        var retired=OwnedWaterHeights.cache(owned,random);CoarseHeightField.reset();
        retired.set(0,0,first);var fresh=OwnedWaterHeights.cache(owned,random);
        result.addProperty("resetRejectsOldPublication",fresh!=retired&&fresh.getIfPresent(0,0)==null);
        var afterReset=(short[])block.invoke(null,0,0,owned,random);
        result.addProperty("resetRecomputedEqual",afterReset!=recomputed&&Arrays.equals(recomputed,afterReset));
        var generators=List.<ChunkGenerator>of(owned,owned,foreign);var randoms=List.of(random,states.getFirst(),random);
        var expected=new ArrayList<float[]>();
        for(int i=0;i<3;i++) {CoarseHeightField.reset();var values=new float[128*128];
            CoarseHeightField.fillWindow(-64,-64,128,128,values,generators.get(i),randoms.get(i));expected.add(values);}
        CoarseHeightField.reset();var tasks=new ArrayList<CompletableFuture<float[]>>();
        for(int i=0;i<3;i++) {final int index=i;tasks.add(CompletableFuture.supplyAsync(()->{
            var values=new float[128*128];CoarseHeightField.fillWindow(-64,-64,128,128,values,generators.get(index),randoms.get(index));return values;}));}
        boolean equal=true;for(int i=0;i<3;i++)equal&=Arrays.equals(expected.get(i),tasks.get(i).get(30,java.util.concurrent.TimeUnit.SECONDS));
        result.addProperty("concurrentWindowsEqualSerial",equal);
        result.addProperty("windowCells",3*128*128);
        return result;
    }

    private static int[] readMapBounds(Path path) throws Exception {
        var config=new ConfigManager(Path.of(".")).loadConfig(path,MapConfig.class);
        var min=MapConfig.class.getMethod("getMinPos").invoke(config);var max=MapConfig.class.getMethod("getMaxPos").invoke(config);
        return new int[]{(int)min.getClass().getMethod("getY").invoke(min),(int)max.getClass().getMethod("getY").invoke(max)};
    }
    private static JsonObject mapBounds(MinecraftServer server) throws Exception {
        var bounds=readMapBounds(Path.of("scope-map.conf"));int minY=bounds[0],maxY=bounds[1];
        var result=new JsonObject();result.addProperty("minY",minY);result.addProperty("maxY",maxY);
        result.addProperty("coversDeepAndTall",minY<=-512&&maxY>=1535);
        var level=server.overworld();result.addProperty("worldMinY",level.getMinBuildHeight());result.addProperty("worldMaxY",level.getMaxBuildHeight());
        result.addProperty("coversWorld",minY<=level.getMinBuildHeight()&&maxY>=level.getMaxBuildHeight()-1);
        var text=Files.readString(Path.of("scope-map.conf")).replaceAll("(?m)^\\s*(?:min-y|max-y):[^\\n]*\\n?","");
        var operator=Path.of("scope-map-operator.conf");Files.writeString(operator,text+"\nmin-y: -16\nmax-y: 128\n");
        var selected=readMapBounds(operator);result.addProperty("operatorCropPreserved",Arrays.equals(new int[]{-16,128},selected));
        return result;
    }

    private void contract(int minimum) throws Exception {
        Files.writeString(contract,"{\"schema\":1,\"roles\":{\"fixture\":{\"minimum\":"+minimum+",\"alternatives\":[{\"template\":\"minecraft:village/plains/houses/plains_small_house_1\",\"type\":\"minecraft:single_pool_element\",\"processors\":\"minecraft:empty\",\"projection\":\"rigid\"}]}}}");
    }
}
