package design.inherent.skyweavecompat.probe;

import com.google.gson.*;
import design.inherent.skyweavecompat.climate.*;
import design.inherent.skyweavecompat.terrain.*;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.block.Blocks;
import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;

/** Actual provider calls and writes; only marked disposable columns receive controlled palettes/support. */
final class SnowClimateProbe {
    private static int feature(ServerLevel level, BlockPos site, Holder<Biome> biome) {
        int minX = site.getX() & -16, minZ = site.getZ() & -16;
        var columns = new ArrayList<BlockPos>();
        for (int dx = 0; dx < 16; dx++) for (int dz = 0; dz < 16; dz++) {
            int x = minX + dx, z = minZ + dz;
            var top = new BlockPos(x,level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,x,z),z);
            level.setBlock(top.below(),Blocks.GRASS_BLOCK.defaultBlockState(),3);level.setBlock(top,Blocks.AIR.defaultBlockState(),3);columns.add(top);
        }
        var context = new net.minecraft.world.level.levelgen.feature.FeaturePlaceContext<>(Optional.empty(),level,
                level.getChunkSource().getGenerator(),net.minecraft.util.RandomSource.create(42),new BlockPos(minX,0,minZ),
                net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration.INSTANCE);
        new net.minecraft.world.level.levelgen.feature.SnowAndFreezeFeature(
                net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration.CODEC).place(context);
        int snow = 0;for (var position : columns) if (level.getBlockState(position).is(Blocks.SNOW)) snow++;
        return snow;
    }
    private static void foreign(ServerLevel level, Path output) throws Exception {
        var biomes = level.registryAccess().registryOrThrow(Registries.BIOME);
        var common = Class.forName("snownee.snow.util.CommonProxy").getMethod("coldEnoughToSnow", LevelReader.class, BlockPos.class, Holder.class);
        var tree = Class.forName("bl4ckscor3.mod.snowundertrees.SnowUnderTrees").getMethod("canSnow", WorldGenLevel.class, BlockPos.class);
        var rows = new JsonArray();
        for (var id : List.of(Biomes.BIRCH_FOREST, Biomes.SNOWY_PLAINS, Biomes.DESERT)) {
            var biome = biomes.getHolderOrThrow(id);
            for (int cx = -1; cx <= 1; cx++) for (int cz = -1; cz <= 1; cz++)
                level.getChunk(cx,cz).fillBiomesFromNoise((x,y,z,sampler)->biome, level.getChunkSource().randomState().sampler());
            for (int y : new int[]{80,330,465}) {
                var position = new BlockPos(8,y,8);
                boolean inBuild = y >= level.getMinBuildHeight() && y < level.getMaxBuildHeight();
                if (inBuild) {level.setBlock(position.below(),Blocks.GRASS_BLOCK.defaultBlockState(),3);level.setBlock(position,Blocks.AIR.defaultBlockState(),3);}
                check(IslandClimate.temperaturePosition(level,position) == position, "Foreign world gained projected coordinates");
                boolean raw = biome.value().coldEnoughToSnow(position);
                boolean actual = (boolean)common.invoke(null,level,position,biome);
                check(actual == raw, "Foreign SRM predicate changed " + id + " y=" + y);
                boolean treeSnow = (boolean)tree.invoke(null,level,position);
                check(treeSnow == (inBuild && biome.value().hasPrecipitation() && raw), "Foreign tree predicate changed " + id + " y=" + y);
                check(!SurfaceClimateScope.active() && SurfaceClimateScope.level() == null,"Foreign scope leaked");
                var row = new JsonObject(); row.addProperty("biome",id.location().toString());row.addProperty("y",y);
                row.addProperty("rawCold",raw);row.addProperty("srmCold",actual);row.addProperty("treeSnow",treeSnow);rows.add(row);
            }
        }
        var result = new JsonObject();result.addProperty("complete",true);result.addProperty("world",System.getProperty("neighbors.probe.snowWorld"));
        result.addProperty("generator",level.getChunkSource().getGenerator().getClass().getName());
        result.addProperty("dimensionTypeMatches",IslandClimate.applies(level));result.add("rows",rows);
        Files.writeString(output,new GsonBuilder().setPrettyPrinting().create().toJson(result));
    }
    private static void check(boolean valid, String message) { if (!valid) throw new AssertionError(message); }
    static void run(MinecraftServer server, Path output) throws Exception {
        var level = server.overworld();
        var savedWitness = level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("c1-snow-witness.json");
        boolean reopened = Files.isRegularFile(savedWitness);
        if (reopened) {
            var old = JsonParser.parseString(Files.readString(savedWitness)).getAsJsonArray();
            for (var entry : old) {
                var row = entry.getAsJsonObject();var position = new BlockPos(row.get("x").getAsInt(),row.get("y").getAsInt(),row.get("z").getAsInt());
                check(level.getBlockState(position).toString().equals(row.get("state").getAsString()),"Saved native snow changed before reopen observation");
            }
        }
        if (!System.getProperty("neighbors.probe.snowWorld", "owned").equals("owned")) {
            foreign(level, output); return;
        }
        var generator = (SkyIslandChunkGenerator) level.getChunkSource().getGenerator();
        var field = ((GeologicalGenerator)(Object) generator).skyweave$field(level.getChunkSource().randomState());
        var settings = generator.resolvedSkyIslandSettings();
        var biomeRegistry = level.registryAccess().registryOrThrow(Registries.BIOME);
        Holder<Biome> birch = biomeRegistry.getHolderOrThrow(Biomes.BIRCH_FOREST);
        var sites = new ArrayList<BlockPos>();
        // Bound metadata search, then require a real natural owner and a discriminating native temperature.
        int scanned = 0;
        outer: for (var anchor : field.collectIslandPreviewsInRadius(0, 0, 8000, settings)) {
            if (++scanned > 512) break;
            var context = ((InteriorField)(Object) field).skyweave$contextAt(anchor.x(), anchor.z(), settings);
            for (var body : context.bodies()) {
                var position = new BlockPos(anchor.x(), body.exterior().topY() + 1, anchor.z());
                var ref = ClimateRuntime.temperaturePosition(generator, level.getChunkSource().randomState(), position);
                if (ref.getY() != 64 && birch.value().coldEnoughToSnow(position) && !birch.value().coldEnoughToSnow(ref)) {
                    sites.add(position); if (sites.size() == 2) break outer;
                }
            }
        }
        check(sites.size() == 2, "Need two actual high-island/native-cold/relative-warm witnesses");
        var hooks = Class.forName("snownee.snow.Hooks");
        Method extra = hooks.getMethod("placeFeatureExtra", Biome.class, WorldGenLevel.class, BlockPos.class, BlockPos.class);
        var common = Class.forName("snownee.snow.util.CommonProxy");
        Method cold = common.getMethod("coldEnoughToSnow", LevelReader.class, BlockPos.class, Holder.class);
        Method tree = Class.forName("bl4ckscor3.mod.snowundertrees.SnowUnderTrees").getMethod("canSnow", WorldGenLevel.class, BlockPos.class);
        boolean repaired = System.getProperty("neighbors.probe.snowClimate").equals("repaired");
        var report = new JsonObject(); var rows = new JsonArray(); var saved = new JsonArray();
        report.addProperty("repaired", repaired); report.addProperty("seed", level.getSeed());
        report.addProperty("fixture", "Native provider calls at natural contributor surfaces; controlled native chunk biome palettes and support, not a whole-feature survey");
        for (var site : sites) {
            level.getChunkAt(site); // fixture setup, never part of the measured predicate path
            for (var id : List.of(Biomes.BIRCH_FOREST, Biomes.DARK_FOREST, Biomes.JUNGLE, Biomes.DESERT, Biomes.SNOWY_PLAINS)) {
                var biome = biomeRegistry.getHolderOrThrow(id);
                // Native fuzzy selection can address a neighboring quart/chunk. Control its complete halo.
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
                    level.getChunk((site.getX() >> 4) + dx, (site.getZ() >> 4) + dz)
                            .fillBiomesFromNoise((x,y,z,sampler) -> biome, level.getChunkSource().randomState().sampler());
                check(level.getBiome(site).is(id), "Actual fuzzy biome differs from controlled palette " + id);

                level.setBlock(site.below(), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
                level.setBlock(site, Blocks.AIR.defaultBlockState(), 3);
                var reference = IslandClimate.temperaturePosition(level, site);
                boolean expected = biome.value().coldEnoughToSnow(repaired ? reference : site);
                int loaded = level.getChunkSource().getLoadedChunksCount();
                boolean actual = (boolean) cold.invoke(null, level, site, biome);
                check(actual == expected, "SRM cold mismatch " + id + " at " + site + " reference " + reference);
                boolean treeActual = (boolean) tree.invoke(null, level, site);
                boolean nativePrecipitation = biome.value().hasPrecipitation() && expected;
                check(treeActual == nativePrecipitation, "Tree snow mismatch " + id + " at " + site + " actual=" + treeActual + " expected=" + nativePrecipitation
                        + " biome=" + level.getBiome(site).unwrapKey() + " precipitation=" + biome.value().getPrecipitationAt(site)
                        + " light=" + level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, site)
                        + " support=" + level.getBlockState(site.below()).isFaceSturdy(level, site.below(), net.minecraft.core.Direction.UP));
                extra.invoke(null, biome.value(), level, site, site.below());
                var state = level.getBlockState(site);
                check(level.getChunkSource().getLoadedChunksCount() == loaded, "Snow predicate/placement loaded a chunk");
                check(!SurfaceClimateScope.active() && SurfaceClimateScope.level() == null, "Climate scope leaked");
                if (id.equals(Biomes.BIRCH_FOREST))
                    check(state.is(Blocks.SNOW) == !repaired, "SRM extra placement did not discriminate original/repaired warm island");
                if (id.equals(Biomes.SNOWY_PLAINS)) check(state.is(Blocks.SNOW), "Cold native extra snow lost");
                if (id.equals(Biomes.DESERT)) check(state.isAir(), "Desert gained extra snow");
                var row = new JsonObject(); row.addProperty("biome", id.location().toString());
                row.addProperty("x", site.getX()); row.addProperty("physicalY", site.getY()); row.addProperty("z", site.getZ());
                row.addProperty("referenceY", reference.getY()); row.addProperty("rawCold", biome.value().coldEnoughToSnow(site));
                if (id.equals(Biomes.BIRCH_FOREST)) {
                    int generatedSnow = feature(level,site,biome);
                    check(repaired ? generatedSnow == 0 : generatedSnow > 0,"Actual SnowAndFreezeFeature did not discriminate warm island");
                    row.addProperty("nativeFeatureSnowColumns",generatedSnow);
                    // Isolated native melting-option positive; restore native fields before returning/saving.
                    var config = Class.forName("snownee.snow.SnowCommonConfig");
                    var melting = config.getField("snowAndIceMeltInWarmBiomes");
                    boolean wasMelting = melting.getBoolean(null);
                    var melt = common.getMethod("shouldMeltByTemperature",net.minecraft.world.level.Level.class,BlockPos.class,Holder.class,int.class);
                    boolean nativeConfigured = (boolean)melt.invoke(null,level,site,biome,1);
                    row.addProperty("configuredMelt",nativeConfigured);row.addProperty("warmMeltingOption",wasMelting);
                    try {
                        melting.setBoolean(null,true);
                        boolean optionMelt = (boolean)melt.invoke(null,level,site,biome,1);
                        check(optionMelt == repaired,"Native temperature melting failed controlled warm-island option");
                        row.addProperty("enabledOptionMelt",optionMelt);
                    } finally {melting.setBoolean(null,wasMelting);}
                    check(melting.getBoolean(null)==wasMelting,"Melting option was not restored");
                    // Snowlogging keeps the native container and raw slab state; only temperature admission differs.
                    level.setBlock(site,Blocks.AIR.defaultBlockState(),3);
                    var slab = Blocks.OAK_SLAB.defaultBlockState(); level.setBlock(site.below(),slab,3);
                    extra.invoke(null,biome.value(),level,site,site.below());
                    var below = level.getBlockState(site.below());
                    check(below.is(Blocks.OAK_SLAB)==repaired,"Snowlogged slab did not discriminate raw/relative warm climate");
                    row.addProperty("slabResult",below.toString());
                }
                if (id.equals(Biomes.SNOWY_PLAINS)) {
                    level.setBlock(site,Blocks.AIR.defaultBlockState(),3);
                    var slab = Blocks.OAK_SLAB.defaultBlockState();level.setBlock(site.below(),slab,3);
                    extra.invoke(null,biome.value(),level,site,site.below());
                    var coldSlab = level.getBlockState(site.below());
                    check(!coldSlab.is(Blocks.OAK_SLAB),"Cold snowlogging was lost");
                    row.addProperty("coldSlabResult",coldSlab.toString());
                    // End with a real provider-written cold snow layer for the saved-file witness.
                    level.setBlock(site.below(),Blocks.GRASS_BLOCK.defaultBlockState(),3);level.setBlock(site,Blocks.AIR.defaultBlockState(),3);
                    extra.invoke(null,biome.value(),level,site,site.below());check(level.getBlockState(site).is(Blocks.SNOW),"Cold snow save witness missing");
                }
                row.addProperty("srmCold", actual); row.addProperty("treeSnow", treeActual); row.addProperty("savedBlock", state.toString()); rows.add(row);
            }
            var witness = new JsonObject();witness.addProperty("x",site.getX());witness.addProperty("y",site.getY());witness.addProperty("z",site.getZ());
            witness.addProperty("state",level.getBlockState(site).toString());saved.add(witness);
        }
        Files.writeString(savedWitness,new Gson().toJson(saved));
        report.addProperty("savedReopenChecked",reopened);
        report.add("rows", rows); report.addProperty("scannedMetadataAnchors", scanned);
        report.addProperty("complete", true);
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }
}
