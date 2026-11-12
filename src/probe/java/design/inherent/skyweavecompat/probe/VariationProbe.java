package design.inherent.skyweavecompat.probe;

import com.google.gson.*;
import design.inherent.skyweavecompat.terrain.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;
import org.sathrek.sky_archipelago.worldgen.generator.field.model.IslandDescriptor;
import java.nio.file.*;
import java.util.*;

/** Focused real sampler/fill/feature fixture, with explicit rare-biome/profile inputs. */
public final class VariationProbe {
    private static final java.util.concurrent.atomic.AtomicReference<JsonObject> GENERATION_CONTROLS=new java.util.concurrent.atomic.AtomicReference<>();
    static void run(MinecraftServer server, Path output) throws Exception {
        long began = System.nanoTime();
        var level = server.overworld();
        var generator = (SkyIslandChunkGenerator)level.getChunkSource().getGenerator();
        var settings = generator.resolvedSkyIslandSettings();
        var random = level.getChunkSource().randomState();
        var field = ((GeologicalGenerator)(Object)generator).skyweave$field(random);
        var styles = ((StyledTerrain)(Object)field).skyweave$styles();
        var kind = IslandStyles.Kind.valueOf(System.getProperty("neighbors.probe.variationKind", "FRACTURED"));
        boolean caveBiomes=design.inherent.skyweavecompat.OptionalIntegrations.enabled(design.inherent.skyweavecompat.Feature.CAVE_BIOMES);
        if(!caveBiomes) {
            if(design.inherent.skyweavecompat.OptionalIntegrations.selection().mixins(false).stream().anyMatch(name->name.startsWith("DeepDark")))
                throw new AssertionError("Disabled cave adapter selected special-habitat mixins");
            for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++)for(var member:((ClusterField)(Object)field).skyweave$members(x,z,settings))
                if(styles.style(member).kind()==IslandStyles.Kind.DEEP_DARK)throw new AssertionError("Disabled cave adapter promoted a special island");
        }
        IslandDescriptor island = null;
        search: for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) {
            var members = ((ClusterField)(Object)field).skyweave$members(x, z, settings);
            if (!members.isEmpty() && styles.style(members.getFirst()).kind() == kind) { island = members.getFirst(); break search; }
        }
        if (island == null) throw new AssertionError("No " + kind + " island in bounded explicit fixture");
        if(kind==IslandStyles.Kind.DEEP_DARK) {
            var original=InteriorConfiguration.profile();
            var baseline=new InteriorProfile(0,0,original.shellThickness(),original.noiseHorizontalScale(),original.noiseVerticalScale(),
                    original.passageWidth(),original.chamberThreshold(),original.hollowRadius(),original.hollowHeight(),
                    original.openingChance(),original.nativeCarvers(),original.skylights());
            if(IslandInteriors.kind(island,baseline)!=IslandInteriors.Kind.BASELINE
                    || IslandInteriors.kind(island,baseline,styles.style(island).kind())!=IslandInteriors.Kind.HOLLOW)
                throw new AssertionError("Special planned interior did not override ordinary interior selection");
        }
        var otherGenerator = new SkyIslandChunkGenerator(generator.getBiomeSource(), generator.generatorSettings(), generator.embeddedSettings());
        var otherRandom = RandomState.create(generator.generatorSettings().value(),
                server.registryAccess().registryOrThrow(Registries.NOISE).asLookup(), level.getSeed());
        var other = ((GeologicalGenerator)(Object)otherGenerator).skyweave$field(otherRandom);
        var profile = ((ProfiledTerrain)(Object)field).skyweave$profile();
        var points = new ArrayList<BlockPos>();
        BlockPos cavernWitness = null; int largestCavern = 0;
        int solid = 0, empty = 0;
        for (int ix = -8; ix <= 8; ix++) for (int iz = -8; iz <= 8; iz++) {
            int x = island.centerX() + ix * island.radiusX() / 8, z = island.centerZ() + iz * island.radiusZ() / 8;
            var columns = ((InteriorField)(Object)field).skyweave$exteriorAt(x, z, settings);
            if (columns.isEmpty()) { empty++; continue; }
            solid++;
            if(kind==IslandStyles.Kind.DEEP_DARK) {
                var natural=((InteriorField)(Object)field).skyweave$contextAt(x,z,settings);
                int air=0;
                for(var body:natural.bodies())if(body.island().equals(island))for(int y=body.exterior().bottomY();y<=body.exterior().topY();y++)
                    if(natural.naturalAt(body,y)==IslandContext.Occupancy.CAVITY && natural.solidOwner(y)==null)air++;
                if(air>largestCavern) {largestCavern=air;cavernWitness=new BlockPos(x,columns.getFirst().topY(),z);}
            }
            for (var column : columns) if (column.bottomY() < GenerationProfile.MIN_Y + profile.floorClearance()
                    || column.topY() > GenerationProfile.MAX_Y - 1 - profile.buildingHeadroom())
                throw new AssertionError("Fractured exterior exceeds vertical envelope");
            if (points.size() < 2 || ix == 0 && iz == 0) points.add(new BlockPos(x, columns.getFirst().topY(), z));
        }
        if (solid < 20 || empty < 10) throw new AssertionError("Missing usable remnants or gaps");
        if(kind==IslandStyles.Kind.DEEP_DARK) {
            if(cavernWitness==null)throw new AssertionError("No intrinsic cavern in full bounded island survey: "+island);
            if(!points.contains(cavernWitness))points.add(cavernWitness);
        }
        var rows = new JsonArray(); long compared = 0;
        long deepBody = 0, cavityAir = 0, capSculk = 0, capRock = 0, surfaceStageColumns = 0, savedSculk = 0;
        JsonObject habitatControls = null;
        var allDevices = new TreeMap<String,Integer>();
        for (var point : points) {
            int x = point.getX(), z = point.getZ(); var chunkPos = new ChunkPos(point);
            var snapshot = other.sampleChunkTerrainSnapshot(chunkPos, settings);
            var direct = field.sampleSolidSegments(x, z, settings);
            if (!snapshot.segmentsAt(x, z).equals(direct) || !other.sampleSolidSegments(x, z, settings).equals(direct))
                throw new AssertionError("Reordered completed/snapshot columns differ");
            var proto = new ProtoChunk(chunkPos, UpgradeData.EMPTY, level,
                    server.registryAccess().registryOrThrow(Registries.BIOME), null);
            generator.fillFromNoise(Blender.empty(), random, level.structureManager(), proto).join();
            var pos = new BlockPos.MutableBlockPos();
            for (int bx = chunkPos.getMinBlockX(); bx <= chunkPos.getMaxBlockX(); bx++)
                for (int bz = chunkPos.getMinBlockZ(); bz <= chunkPos.getMaxBlockZ(); bz++) {
                    var base = generator.getBaseColumn(bx, bz, level, random);
                    var natural = ((InteriorField)(Object)field).skyweave$contextAt(bx,bz,settings);
                    for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y++) {
                        var actual = proto.getBlockState(pos.set(bx,y,bz));
                        if (actual != base.getBlock(y))
                            throw new AssertionError("Fractured fill/base-column mismatch at " + pos);
                        if (kind == IslandStyles.Kind.DEEP_DARK) {
                            if (DeepDarkMaterials.special(styles,natural.solidOwner(y)) && actual.is(Blocks.STONE))
                                throw new AssertionError("Special contributor retained ordinary stone at "+pos);
                            if (DeepDarkMaterials.special(styles,natural.solidOwner(y)) && actual.is(Blocks.DEEPSLATE)) deepBody++;
                            if (natural.solidOwner(y)==null && natural.contributions(y).stream().anyMatch(value->
                                    value.occupancy()==IslandContext.Occupancy.CAVITY && DeepDarkMaterials.special(styles,value.body()))) {
                                if (!actual.isAir()) throw new AssertionError("Material rule refilled a cavern at "+pos);
                                cavityAir++;
                            }
                        }
                        compared++;
                    }
                }
            var surfaced = level.getChunk(chunkPos.x,chunkPos.z,ChunkStatus.SURFACE,true);
            // A reopened FULL chunk is not a fresh SURFACE-stage witness. Later native ore/features
            // can legitimately add gravel and other material; the full saved digest checks them separately.
            if (kind==IslandStyles.Kind.DEEP_DARK && surfaced.getPersistedStatus()==ChunkStatus.SURFACE)
                for (int bx=chunkPos.getMinBlockX();bx<=chunkPos.getMaxBlockX();bx++)
                for (int bz=chunkPos.getMinBlockZ();bz<=chunkPos.getMaxBlockZ();bz++) {
                    var natural=((InteriorField)(Object)field).skyweave$contextAt(bx,bz,settings);
                    for(var segment:field.sampleSolidSegments(bx,bz,settings)) {
                        var owner=natural.solidOwner(segment.topY());
                        if(!DeepDarkMaterials.special(styles,owner))continue;
                        surfaceStageColumns++;
                        var state=surfaced.getBlockState(pos.set(bx,segment.topY(),bz));
                        if(state.is(Blocks.SCULK))capSculk++;
                        else if(state.is(Blocks.DEEPSLATE))capRock++;
                        else if(NaturalMaterials.skin(state))throw new AssertionError("Special cap retained ordinary skin at "+pos+": "+state);
                    }
                }
            var chunk = level.getChunk(chunkPos.x, chunkPos.z);
            if (kind==IslandStyles.Kind.DEEP_DARK && habitatControls==null)
                habitatControls=habitatControls(level,generator,field,styles,chunkPos);
            var blocks = new TreeMap<String, Integer>(); var biomes = new TreeMap<String, Integer>();
            for (int bx = chunkPos.getMinBlockX(); bx <= chunkPos.getMaxBlockX(); bx++)
                for (int bz = chunkPos.getMinBlockZ(); bz <= chunkPos.getMaxBlockZ(); bz++) {
                    var exterior = ((InteriorField)(Object)field).skyweave$exteriorAt(bx, bz, settings);
                    if (exterior.isEmpty()) continue;
                    int top = exterior.getFirst().topY();
                    for (int dy = -3; dy <= 3; dy++) {
                        var state = chunk.getBlockState(pos.set(bx, top + dy, bz));
                        if (!state.isAir()) blocks.merge(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(), 1, Integer::sum);
                    }
                    var stored = chunk.getNoiseBiome(bx >> 2, top >> 2, bz >> 2);
                    var physical = generator.getBiomeSource().getNoiseBiome(bx >> 2, top >> 2, bz >> 2, random.sampler());
                    if (!stored.is(physical.unwrapKey().orElseThrow())) throw new AssertionError("Stored/physical ecological biome mismatch");
                    biomes.merge(stored.getRegisteredName(), 1, Integer::sum);
                }
            var row = new JsonObject(); row.add("position", new Gson().toJsonTree(point));
            var saved = new TreeMap<String,Integer>();
            var devices = new TreeMap<String,Integer>();
            var digest=java.security.MessageDigest.getInstance("SHA-256");
            for(int bx=chunkPos.getMinBlockX();bx<=chunkPos.getMaxBlockX();bx++)
                for(int bz=chunkPos.getMinBlockZ();bz<=chunkPos.getMaxBlockZ();bz++)
                    for(int y=level.getMinBuildHeight();y<level.getMaxBuildHeight();y++) {
                        var state=chunk.getBlockState(pos.set(bx,y,bz));
                        int id=net.minecraft.world.level.block.Block.getId(state);
                        digest.update((byte)(id>>>24));digest.update((byte)(id>>>16));digest.update((byte)(id>>>8));digest.update((byte)id);
                        if(!state.isAir())saved.merge(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(),1,Integer::sum);
                        if(state.is(Blocks.SCULK))savedSculk++;
                        if(kind==IslandStyles.Kind.DEEP_DARK && (state.is(Blocks.SCULK_VEIN)||state.is(Blocks.SCULK_SENSOR)
                                ||state.is(Blocks.SCULK_CATALYST)||state.is(Blocks.SCULK_SHRIEKER)) && DeepDarkHabitat.contains(level,pos))
                            devices.merge(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(),1,Integer::sum);
                        if(state.is(Blocks.SCULK_SHRIEKER) && DeepDarkHabitat.contains(level,pos)
                                && !state.getValue(net.minecraft.world.level.block.SculkShriekerBlock.CAN_SUMMON))
                            throw new AssertionError("Natural special shrieker lost native summon behavior");
                    }
            row.add("savedBlocks",new Gson().toJsonTree(saved));row.addProperty("savedBlockDigest",HexFormat.of().formatHex(digest.digest()));
            row.add("specialHabitatDevices",new Gson().toJsonTree(devices));
            if(kind==IslandStyles.Kind.DEEP_DARK && devices.values().stream().anyMatch(count->count>1))
                throw new AssertionError("Natural special-habitat device budget exceeded in "+chunkPos+": "+devices);
            devices.forEach((key,value)->allDevices.merge(key,value,Integer::sum));
            row.add("surfaceBlocks", new Gson().toJsonTree(blocks)); row.add("surfaceBiomes", new Gson().toJsonTree(biomes)); rows.add(row);
        }
        var report = new JsonObject(); report.addProperty("complete", true); report.addProperty("kind", kind.name());
        report.add("island", new Gson().toJsonTree(island)); report.add("style", new Gson().toJsonTree(styles.style(island)));
        report.addProperty("solidSampleColumns", solid); report.addProperty("emptySampleColumns", empty);
        report.addProperty("comparedNoiseBlocks", compared); report.add("samples", rows);
        report.addProperty("caveBiomesAdapterActive",caveBiomes);
        report.addProperty("largestSampledCavern",largestCavern);
        if(kind==IslandStyles.Kind.DEEP_DARK) {
            if(deepBody<1000 || cavityAir<100 || savedSculk<100 || surfaceStageColumns>0 && capSculk<100)
                throw new AssertionError("Missing positive Deep Dark body/cavern/cap witnesses: "+deepBody+"/"+cavityAir+"/"+capSculk+"/"+savedSculk);
            report.addProperty("deepBodyBlocks",deepBody);report.addProperty("cavernAirBlocks",cavityAir);
            report.addProperty("surfaceSculk",capSculk);report.addProperty("surfaceRock",capRock);
            report.addProperty("surfaceStageColumns",surfaceStageColumns);report.addProperty("savedSculk",savedSculk);
            if(habitatControls==null)throw new AssertionError("No real surface habitat control");
            report.add("habitatControls",habitatControls);
            var generation=GENERATION_CONTROLS.get();
            // Reopened saved chunks need not decorate again; the runner retains first-boot controls.
            if(surfaceStageColumns>0 && generation==null)throw new AssertionError("Missing actual WorldGenRegion feature control");
            if(generation!=null)report.add("generationControls",generation);
            var tuning=styles.profile().deepDark();
            var chances=Map.of("minecraft:sculk_vein",tuning.veinChance(),"minecraft:sculk_sensor",tuning.sensorChance(),
                    "minecraft:sculk_catalyst",tuning.catalystChance(),"minecraft:sculk_shrieker",tuning.shriekerChance());
            chances.forEach((key,chance)->{
                int count=allDevices.getOrDefault(key,0);
                if(chance==0 && count!=0 || chance==1 && count==0)throw new AssertionError("Device endpoint not observed: "+key+" "+chance+" / "+count);
            });
            report.add("specialHabitatDevices",new Gson().toJsonTree(allDevices));
        }
        report.addProperty("elapsedSeconds", (System.nanoTime() - began) / 1e9);
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
    }

    private static JsonObject habitatControls(net.minecraft.server.level.ServerLevel level,SkyIslandChunkGenerator generator,
            org.sathrek.sky_archipelago.worldgen.generator.field.IslandField field,IslandStyles styles,ChunkPos chunk) {
        var settings=generator.resolvedSkyIslandSettings();
        for(int x=chunk.getMinBlockX()+4;x<=chunk.getMaxBlockX()-4;x++)for(int z=chunk.getMinBlockZ()+4;z<=chunk.getMaxBlockZ()-4;z++) {
            var context=((InteriorField)(Object)field).skyweave$contextAt(x,z,settings);
            for(var segment:field.sampleSolidSegments(x,z,settings)) {
                var floor=new BlockPos(x,segment.topY(),z);var pos=floor.above();
                if(!DeepDarkMaterials.special(styles,context.solidOwner(floor.getY())) || !level.getBlockState(floor).is(Blocks.SCULK)
                        || !level.getBlockState(pos).isAir() || !level.canSeeSky(pos))continue;
                var volume=new LinkedHashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();boolean usable=true;
                for(int dx=-4;dx<=4;dx++)for(int dz=-4;dz<=4;dz++)for(int dy=0;dy<16;dy++) {
                    var at=pos.offset(dx,dy,dz);var state=level.getBlockState(at);
                    if(state.hasBlockEntity() || at.getY()>=level.getMaxBuildHeight())usable=false;
                    volume.put(at,state);
                }
                if(!usable)continue;
                var before=level.getBlockState(floor);long time=level.getDayTime();
                try {
                    level.setDayTime(6000);level.setBlock(floor,Blocks.GRASS_BLOCK.defaultBlockState(),2);
                    var random=net.minecraft.util.RandomSource.create(41);
                    if(!net.minecraft.world.entity.animal.Animal.checkAnimalSpawnRules(net.minecraft.world.entity.EntityType.COW,level,
                            net.minecraft.world.entity.MobSpawnType.NATURAL,pos,random))continue;
                    for(var reason:List.of(net.minecraft.world.entity.MobSpawnType.NATURAL,net.minecraft.world.entity.MobSpawnType.CHUNK_GENERATION))
                        if(net.minecraft.world.entity.SpawnPlacements.checkSpawnRules(net.minecraft.world.entity.EntityType.COW,level,reason,pos,random))
                            throw new AssertionError("Passive natural spawn allowed on special island: "+reason);
                    for(var reason:List.of(net.minecraft.world.entity.MobSpawnType.BREEDING,net.minecraft.world.entity.MobSpawnType.COMMAND))
                        if(!net.minecraft.world.entity.SpawnPlacements.checkSpawnRules(net.minecraft.world.entity.EntityType.COW,level,reason,pos,random))
                            throw new AssertionError("Player-brought spawn was suppressed: "+reason);
                    var provider=net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider.simple(Blocks.SHORT_GRASS);
                    var config=new net.minecraft.world.level.levelgen.feature.configurations.SimpleBlockConfiguration(provider);
                    var feature=new net.minecraft.world.level.levelgen.feature.ConfiguredFeature<>(net.minecraft.world.level.levelgen.feature.Feature.SIMPLE_BLOCK,config);
                    if(!feature.place(level,generator,random,pos)||!level.getBlockState(pos).is(Blocks.SHORT_GRASS))
                        throw new AssertionError("Runtime plant feature was suppressed");
                    level.setBlock(pos,Blocks.AIR.defaultBlockState(),2);
                    var placed=new boolean[1];
                    DeepDarkHabitat.authored(()->placed[0]=feature.place(level,generator,random,pos));
                    if(!placed[0]||!level.getBlockState(pos).is(Blocks.SHORT_GRASS))throw new AssertionError("Authored plant was suppressed");
                    level.setBlock(pos,Blocks.AIR.defaultBlockState(),2);
                    volume.keySet().forEach(at->level.setBlock(at,Blocks.AIR.defaultBlockState(),2));
                    var sapling=Blocks.BIRCH_SAPLING.defaultBlockState().setValue(net.minecraft.world.level.block.SaplingBlock.STAGE,1);
                    level.setBlock(pos,sapling,2);
                    ((net.minecraft.world.level.block.SaplingBlock)Blocks.BIRCH_SAPLING).performBonemeal(level,net.minecraft.util.RandomSource.create(0),pos,sapling);
                    if(volume.keySet().stream().noneMatch(at->level.getBlockState(at).is(Blocks.BIRCH_LOG)))throw new AssertionError("Native sapling bonemeal did not grow a tree");
                    volume.keySet().forEach(at->level.setBlock(at,Blocks.AIR.defaultBlockState(),2));
                    var mushroom=Blocks.BROWN_MUSHROOM.defaultBlockState();level.setBlock(pos,mushroom,2);
                    ((net.minecraft.world.level.block.MushroomBlock)Blocks.BROWN_MUSHROOM).performBonemeal(level,net.minecraft.util.RandomSource.create(0),pos,mushroom);
                    if(volume.keySet().stream().noneMatch(at->level.getBlockState(at).is(Blocks.BROWN_MUSHROOM_BLOCK)))throw new AssertionError("Native mushroom bonemeal did not grow");
                    volume.keySet().forEach(at->level.setBlock(at,Blocks.AIR.defaultBlockState(),2));
                    ((net.minecraft.world.level.block.GrassBlock)Blocks.GRASS_BLOCK).performBonemeal(level,net.minecraft.util.RandomSource.create(0),floor,Blocks.GRASS_BLOCK.defaultBlockState());
                    if(volume.keySet().stream().noneMatch(at->level.getBlockState(at).getBlock() instanceof net.minecraft.world.level.block.BushBlock))
                        throw new AssertionError("Native grass bonemeal did not place vegetation");
                    var result=new JsonObject();result.addProperty("nativePassiveBaseline",true);result.addProperty("naturalAndChunkGenerationDenied",true);
                    result.addProperty("breedingAndCommandRetained",true);result.addProperty("runtimePlantRetained",true);result.addProperty("authoredPlantRetained",true);
                    result.addProperty("saplingMushroomAndGrassBonemeal",true);result.add("position",new Gson().toJsonTree(pos));return result;
                } finally {volume.forEach((at,state)->level.setBlock(at,state,2));level.setBlock(floor,before,2);level.setDayTime(time);}
            }
        }
        return null;
    }

    public static void generationControls(net.minecraft.world.level.WorldGenLevel level,SkyIslandChunkGenerator generator,
            net.minecraft.world.level.chunk.ChunkAccess chunk) {
        if(!(level instanceof net.minecraft.server.level.WorldGenRegion) || GENERATION_CONTROLS.get()!=null)return;
        var field=((GeologicalGenerator)(Object)generator).skyweave$field(level.getLevel().getChunkSource().randomState());
        var snapshot=field.sampleChunkTerrainSnapshot(chunk.getPos(),generator.resolvedSkyIslandSettings());
        for(int x=chunk.getPos().getMinBlockX();x<=chunk.getPos().getMaxBlockX();x++)for(int z=chunk.getPos().getMinBlockZ();z<=chunk.getPos().getMaxBlockZ();z++) {
            var segments=snapshot.segmentsAt(x,z);if(segments.isEmpty())continue;
            var floor=new BlockPos(x,segments.getFirst().topY(),z);var at=floor.above();
            if(!level.getBlockState(floor).is(Blocks.SCULK)||!level.getBlockState(at).isAir()||!DeepDarkHabitat.contains(level,at))continue;
            var previous=level.getBlockState(floor);
            try {
                level.setBlock(floor,Blocks.GRASS_BLOCK.defaultBlockState(),2);
                var feature=new net.minecraft.world.level.levelgen.feature.ConfiguredFeature<>(net.minecraft.world.level.levelgen.feature.Feature.SIMPLE_BLOCK,
                        new net.minecraft.world.level.levelgen.feature.configurations.SimpleBlockConfiguration(
                                net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider.simple(Blocks.SHORT_GRASS)));
                var random=net.minecraft.util.RandomSource.create(31);
                if(feature.place(level,generator,random,at)||!level.getBlockState(at).isAir())throw new AssertionError("Generation plant was not excluded");
                var placed=new boolean[1];DeepDarkHabitat.authored(()->placed[0]=feature.place(level,generator,random,at));
                if(!placed[0]||!level.getBlockState(at).is(Blocks.SHORT_GRASS))throw new AssertionError("Authored generation plant was suppressed");
                level.setBlock(at,Blocks.AIR.defaultBlockState(),2);
                var tree=level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).getHolderOrThrow(net.minecraft.data.worldgen.features.TreeFeatures.OAK).value();
                var untouched=net.minecraft.util.RandomSource.create(31);var blocked=net.minecraft.util.RandomSource.create(31);
                if(tree.place(level,generator,blocked,at)||blocked.nextLong()!=untouched.nextLong())throw new AssertionError("Generation tree executed or consumed draw");
                var result=new JsonObject();result.addProperty("actualWorldGenRegion",true);result.addProperty("plantDenied",true);
                result.addProperty("authoredPlantRetained",true);result.addProperty("treeDeniedWithoutDraw",true);
                GENERATION_CONTROLS.compareAndSet(null,result);return;
            } finally {level.setBlock(at,Blocks.AIR.defaultBlockState(),2);level.setBlock(floor,previous,2);}
        }
    }
    private VariationProbe() {}
}
