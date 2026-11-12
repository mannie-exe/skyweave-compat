package design.inherent.skyweavecompat.probe;

import com.google.gson.JsonObject;
import design.inherent.skyweavecompat.climate.IslandClimate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;

/** Native predicates at authored high-altitude water/snow fixtures in a disposable world. */
final class ClimateProbe {
    private ClimateProbe() {}
    private static void check(boolean valid, String message) { if (!valid) throw new AssertionError(message); }
    static JsonObject run(ServerLevel level) {
        var biomes = level.registryAccess().registryOrThrow(Registries.BIOME);
        var temperate = biomes.getOrThrow(Biomes.BIRCH_FOREST);
        var frozen = biomes.getOrThrow(Biomes.SNOWY_PLAINS);
        var water = new BlockPos(8, 508, 8);
        var snow = water.offset(2, 0, 0);
        level.setBlockAndUpdate(water.below(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(water, Blocks.WATER.defaultBlockState());
        level.setBlockAndUpdate(snow.below(), Blocks.GRASS_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(snow, Blocks.AIR.defaultBlockState());
        check(IslandClimate.applies(level), "Climate did not recognize the custom dimension type");
        check(temperate.coldEnoughToSnow(water), "Fixture does not reproduce the original altitude cooling");
        check(!temperate.shouldFreeze(level, water, false), "Temperate water froze at inhabited altitude");
        check(!temperate.shouldSnow(level, snow), "Temperate island accumulated snow");
        check(frozen.shouldFreeze(level, water, false), "Native cold biome lost freezing");
        check(frozen.shouldSnow(level, snow), "Native cold biome lost snow");
        for (var key : java.util.List.of(Biomes.BADLANDS, Biomes.STONY_SHORE)) {
            var biome = biomes.getOrThrow(key);
            check(!biome.shouldFreeze(level, water, false), "Warm biome froze at inhabited altitude: " + key);
            check(!biome.shouldSnow(level, snow), "Warm biome accumulated snow: " + key);
        }
        check(!IslandClimate.applies(level.getServer().getLevel(net.minecraft.world.level.Level.NETHER)), "Climate leaked into the Nether");
        var result = new JsonObject();
        result.addProperty("fixtureY", water.getY());
        result.addProperty("referenceY", IslandClimate.temperaturePosition(level, water).getY());
        result.addProperty("nativeAltitudeCoolingReproduced", true);
        result.addProperty("temperateDoesNotFreezeOrSnow", true);
        result.addProperty("coldBiomeStillFreezesAndSnows", true);
        result.addProperty("badlandsAndStonyShoreStayWarm", true);
        if (level.getChunkSource().getGenerator() instanceof org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator generator) {
            var field = (design.inherent.skyweavecompat.terrain.InteriorField)(Object)
                    ((design.inherent.skyweavecompat.terrain.GeologicalGenerator)(Object)generator)
                            .skyweave$field(level.getChunkSource().randomState());
            var raw = (org.sathrek.sky_archipelago.worldgen.generator.field.IslandField)(Object)field;
            var anchors = raw.collectIslandPreviewsInRadius(0,0,8000,generator.resolvedSkyIslandSettings());
            int queries = 0; long began = System.nanoTime();
            int loaded = level.getChunkSource().getLoadedChunksCount();
            for (var anchor : anchors) {
                var context = field.skyweave$contextAt(anchor.x(),anchor.z(),generator.resolvedSkyIslandSettings());
                for (var body : context.bodies()) {
                    var at = new BlockPos(anchor.x(),body.climateDatum()+24,anchor.z());
                    var selected = context.biomeOwner(at.getY());
                    if(selected==null)selected=context.owner(at.getY(),design.inherent.skyweavecompat.climate.ClimateConfiguration.profile().airReach());
                    if (selected != body) continue;
                    int expected=design.inherent.skyweavecompat.climate.ClimateColumn.referenceY(at.getY(),body.climateDatum(),
                            design.inherent.skyweavecompat.climate.ClimateConfiguration.profile());
                    check(expected!=64,"Metadata test must distinguish a real thermal result from the missing-data fallback");
                    check(design.inherent.skyweavecompat.climate.ClimateRuntime.temperaturePosition(generator,level.getChunkSource().randomState(),at).getY() == expected,
                            "Natural metadata altitude did not retain local hill cooling");
                    // Read-only remote metadata queries must not request native chunks.
                    for (int n = 0; n < 100; n++) {
                        check(design.inherent.skyweavecompat.climate.ClimateRuntime.temperaturePosition(generator,level.getChunkSource().randomState(),at).getY() == expected,
                                "Warm metadata query changed its altitude");
                        queries++;
                    }
                }
                if (queries >= 1000) break;
            }
            check(queries >= 100,"No actual contributor climate fixture found");
            check(level.getChunkSource().getLoadedChunksCount() == loaded,"Climate lookup loaded chunks");
            result.addProperty("naturalDatumQueries",queries);
            result.addProperty("queryPath","Explicit generator metadata at datum+24; expected reference differs from missing-data64");
            result.addProperty("naturalDatumQueryNanos",System.nanoTime()-began);
            result.addProperty("metadataDidNotLoadChunks",true);
            var point=anchors.stream().filter(anchor->!field.skyweave$contextAt(anchor.x(),anchor.z(),generator.resolvedSkyIslandSettings()).bodies().isEmpty())
                    .findFirst().orElseThrow();
            try { result.add("contextCache",ContextCacheProbe.run(level,point.x(),point.z())); }
            catch(Exception failure) { throw new IllegalStateException("Native context cache check failed",failure); }
        }
        return result;
    }
}
