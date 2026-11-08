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
        return result;
    }
}
