package design.inherent.skyweavecompat.probe;

import org.sathrek.sky_archipelago.worldgen.generator.field.model.IslandFamily;
import com.google.gson.*;
import design.inherent.skyweavecompat.terrain.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;
import java.nio.file.*;
import java.util.*;

/** Target native cave-biome footprints before inspecting stored biomes and actual decoration. */
final class BiomeProbe {
    private BiomeProbe() {}
    static void run(MinecraftServer server, Path output) throws Exception {
        var level = server.overworld();
        var generator = (SkyIslandChunkGenerator)level.getChunkSource().getGenerator();
        var climateIndex=ClimateIndexProbe.run(level,generator,output);
        var random = level.getChunkSource().randomState();
        var settings = generator.resolvedSkyIslandSettings();
        var field = ((GeologicalGenerator)(Object)generator).skyweave$field(random);
        var targets = Set.of("minecraft:lush_caves", "minecraft:dripstone_caves", "minecraft:deep_dark");
        var found = new LinkedHashMap<String, BlockPos>();
        var anchors = field.collectIslandPreviewsInRadius(0, 0, 16000, settings).stream()
                .filter(p -> p.family() == IslandFamily.ANCHOR_PLATEAU)
                .sorted(Comparator.comparingDouble(p -> Math.hypot(p.x(), p.z()))).toList();
        outer: for (var anchor : anchors) for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            int x = ((anchor.x() + dx * anchor.radius() / 2) >> 2) * 4 + 2;
            int z = ((anchor.z() + dz * anchor.radius() / 2) >> 2) * 4 + 2;
            var context = ((InteriorField)(Object)field).skyweave$contextAt(x,z,settings);
            for (var body : context.bodies()) {
                if (body.exterior().thickness() < 100) continue;
                // Search actual projected biome queries in this body's world-space height range.
                // A fixed-Y proxy can identify a native climate candidate without proving that
                // the projected/stored biome at the floating island actually contains it.
                for (int i = 1; i <= 9; i++) {
                    int y = ((body.exterior().bottomY() + body.exterior().thickness() * i / 10) >> 2) * 4 + 2;
                    if (y > body.exterior().topY() - 16) continue;
                    var biome = generator.projectedBiomeSource().getNoiseBiome(x >> 2,y >> 2,z >> 2,random.sampler());
                    if (targets.contains(biome.getRegisteredName()))
                        found.putIfAbsent(biome.getRegisteredName(),new BlockPos(x,y,z));
                    if (found.size() == targets.size()) break outer;
                }
            }
        }
        var rows = new JsonArray(); var pos = new BlockPos.MutableBlockPos();
        for (var entry : found.entrySet()) {
            var point = entry.getValue();
            var chunk = level.getChunk(point.getX() >> 4, point.getZ() >> 4);
            var row = new JsonObject(); row.addProperty("target", entry.getKey()); row.add("position", new Gson().toJsonTree(point));
            row.addProperty("unprojected", generator.getBiomeSource().getNoiseBiome(point.getX() >> 2, point.getY() >> 2,
                    point.getZ() >> 2, random.sampler()).getRegisteredName());
            row.addProperty("stored", chunk.getNoiseBiome(point.getX() >> 2, point.getY() >> 2, point.getZ() >> 2).getRegisteredName());
            int surface = field.sampleColumnProfile(point.getX(), point.getZ(), settings).topY();
            row.addProperty("surfaceUnchanged", chunk.getNoiseBiome(point.getX() >> 2, surface >> 2, point.getZ() >> 2)
                    .equals(generator.projectedBiomeSource().getNoiseBiome(point.getX() >> 2, surface >> 2, point.getZ() >> 2, random.sampler())));
            var blocks = new TreeMap<String, Integer>(); var caveBiomes = new TreeMap<String, Integer>();
            for (int x = chunk.getPos().getMinBlockX(); x <= chunk.getPos().getMaxBlockX(); x++)
                for (int z = chunk.getPos().getMinBlockZ(); z <= chunk.getPos().getMaxBlockZ(); z++)
                    for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y++) {
                        String id = BuiltInRegistries.BLOCK.getKey(chunk.getBlockState(pos.set(x, y, z)).getBlock()).toString();
                        if (id.contains("moss") || id.contains("dripstone") || id.contains("sculk") || id.contains("cave_vine")
                                || id.contains("spore_blossom") || id.contains("azalea") || id.equals("minecraft:clay")) blocks.merge(id, 1, Integer::sum);
                        if ((x & 3) == 2 && (y & 3) == 2 && (z & 3) == 2) {
                            String biome = chunk.getNoiseBiome(x >> 2, y >> 2, z >> 2).getRegisteredName();
                            if (targets.contains(biome)) caveBiomes.merge(biome, 1, Integer::sum);
                        }
                    }
            row.add("blocks", new Gson().toJsonTree(blocks)); row.add("caveBiomeCells", new Gson().toJsonTree(caveBiomes)); rows.add(row);
        }
        var report = new JsonObject(); report.addProperty("seed", level.getSeed()); report.add("samples", rows);
        report.add("climateIndex",climateIndex);
        var environments=new JsonArray();
        for(var dimension:server.getAllLevels()) {
            var environment=new JsonObject();environment.addProperty("dimension",dimension.dimension().location().toString());
            environment.addProperty("dimensionType",dimension.dimensionTypeRegistration().getRegisteredName());
            environment.addProperty("islandClimate",design.inherent.skyweavecompat.climate.IslandClimate.applies(dimension));
            environment.addProperty("generator",dimension.getChunkSource().getGenerator().getClass().getName());environments.add(environment);
        }
        report.add("environments",environments);
        var features = FeatureProbe.run(level);
        report.add("featureChecks", features);
        report.add("climateChecks", ClimateProbe.run(level));
        report.addProperty("targetsFound", found.size());
        boolean matches = rows.asList().stream().allMatch(value -> {
            var row = value.getAsJsonObject();
            return row.get("target").equals(row.get("stored")) && row.get("surfaceUnchanged").getAsBoolean();
        });
        for (String key : new String[]{"temperatePortal", "coldPortal"}) {
            if (!features.has(key)) { matches = false; continue; }
            var portal = features.getAsJsonObject(key);
            matches &= portal.get("cold").equals(portal.get("expectedCold"));
        }
        report.addProperty("complete", found.size() == targets.size() && matches);
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report));
        if (found.size() != targets.size()) throw new AssertionError("Could not find all cave-biome fixtures");
        if (!matches) throw new AssertionError("Cave biome projection or surface preservation failed");
    }
}
