package design.inherent.skyweavecompat.probe;

import org.sathrek.sky_archipelago.worldgen.generator.field.model.IslandFamily;
import com.google.gson.*;
import design.inherent.skyweavecompat.terrain.*;
import design.inherent.skyweavecompat.OptionalIntegrations;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.RandomState;
import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;
import java.nio.file.*;
import java.util.*;

/** Observed generated block counts, kept separate from descriptor/density predictions. */
final class GenerationProbe {
    private GenerationProbe() {}
    private static void check(boolean valid, String message) { if (!valid) throw new AssertionError(message); }

    static void run(MinecraftServer server, Path output) throws Exception {
        var level = server.overworld();
        var generator = level.getChunkSource().getGenerator();
        var report = new JsonObject(); report.addProperty("seed", level.getSeed());
        report.addProperty("profile", System.getProperty("neighbors.probe.generation"));
        report.addProperty("evidence", "observed generated blocks; bounded interior chunk samples");
        report.addProperty("minY", level.getMinBuildHeight()); report.addProperty("maxYExclusive", level.getMaxBuildHeight());
        var sampleLocations = new ArrayList<BlockPos>();
        IslandField field = null;
        SkyIslandChunkGenerator sky = generator instanceof SkyIslandChunkGenerator g ? g : null;
        if (sky != null) {
            field = ((GeologicalGenerator)(Object)sky).skyweave$field(level.getChunkSource().randomState());
            var settings = sky.resolvedSkyIslandSettings();
            var spawn = level.getSharedSpawnPos();
            var anchors = field.collectIslandPreviewsInRadius(spawn.getX(), spawn.getZ(), 4500, settings).stream()
                    .filter(p -> p.family() == IslandFamily.ANCHOR_PLATEAU)
                    .sorted(Comparator.comparingDouble(p -> Math.hypot(p.x() - spawn.getX(), p.z() - spawn.getZ()))).toList();
            report.add("anchors", new Gson().toJsonTree(anchors));
            for (var anchor : anchors.stream().limit(2).toList()) {
                int best = 0; BlockPos chosen = null;
                int step = Math.max(16, anchor.radius() / 6);
                for (int dx = -6; dx <= 6; dx++) for (int dz = -6; dz <= 6; dz++) {
                    int x = anchor.x() + dx * step, z = anchor.z() + dz * step;
                    // Keep sample sites stable when comparing cavity profiles at the same exterior.
                    var column = field.sampleColumnProfile(x, z, settings).terrainColumn();
                    if (column.thickness() > best) { best = column.thickness(); chosen = new BlockPos(x, column.topY(), z); }
                }
                check(chosen != null, "No solid interior sample"); sampleLocations.add(chosen);
            }
        } else {
            var spawn = level.getSharedSpawnPos(); sampleLocations.add(spawn); sampleLocations.add(spawn.offset(256, 0, 0));
        }
        if (System.getProperty("neighbors.probe.generation").equals("adapted")) {
            check(level.getMinBuildHeight() == -128 && level.getMaxBuildHeight() == 768, "Expanded dimension bounds");
            check(sky != null && ((GeologicalGenerator)(Object)sky).skyweave$isEnabled(), "Opt-in generator not enabled");
            var spawn = level.getSharedSpawnPos();
            var landing = new JsonObject();
            landing.add("position", new Gson().toJsonTree(spawn));
            landing.addProperty("floor", BuiltInRegistries.BLOCK.getKey(level.getBlockState(spawn.below()).getBlock()).toString());
            boolean supported = level.getBlockState(spawn.below()).isFaceSturdy(level, spawn.below(), net.minecraft.core.Direction.UP);
            boolean room = level.getBlockState(spawn).getCollisionShape(level, spawn).isEmpty()
                    && level.getBlockState(spawn.above()).getCollisionShape(level, spawn.above()).isEmpty();
            boolean dry = level.getFluidState(spawn).isEmpty() && level.getFluidState(spawn.above()).isEmpty();
            landing.addProperty("supported", supported); landing.addProperty("clearance", room); landing.addProperty("dry", dry);
            report.add("freshSpawn", landing);
            check(supported && room && dry, "Fresh spawn lacks a supported, clear and dry landing: " + spawn);
            TerrainProbe.checkEmbeddedSettings(sky);
            var membership = (ClusterField)(Object)field;
            var settings = sky.resolvedSkyIslandSettings();
            var profile = ((ProfiledTerrain)(Object)field).skyweave$profile();
            BlockPos crossing = null;
            search: for (int cx = -6; cx <= 6; cx++) for (int cz = -6; cz <= 6; cz++) {
                for (var member : membership.skyweave$members(cx, cz, settings)) {
                    if (Math.floorDiv(member.centerX(), profile.spacing()) == cx && Math.floorDiv(member.centerZ(), profile.spacing()) == cz) continue;
                    var column = field.sampleColumn(member.centerX(), member.centerZ(), settings);
                    if (!column.exists()) continue;
                    crossing = new BlockPos(member.centerX(), column.topY(), member.centerZ()); break search;
                }
            }
            check(crossing != null, "Missing generated cross-cell companion fixture");
            sampleLocations.add(crossing); report.add("crossCellCompanion", new Gson().toJsonTree(crossing));
            // Ordinary preset remains unadapted in the same JVM and registries.
            var ordinary = (SkyIslandChunkGenerator)server.registryAccess().registryOrThrow(Registries.WORLD_PRESET)
                    .get(net.minecraft.resources.ResourceLocation.parse("sky_archipelago:sky_islands")).createWorldDimensions().overworld();
            check(!((GeologicalGenerator)(Object)ordinary).skyweave$isEnabled(), "Ordinary preset was modified");
            var nativeField = ((GeologicalGenerator)(Object)ordinary).skyweave$field(level.getChunkSource().randomState());
            check(((ProfiledTerrain)(Object)nativeField).skyweave$profile() == null, "Profile leaked across generators");
        }
        var samples = new JsonArray(); long start = System.nanoTime();
        var mutable = new BlockPos.MutableBlockPos();
        for (var location : sampleLocations) {
            var sample = new JsonObject(); sample.addProperty("x", location.getX()); sample.addProperty("z", location.getZ());
            var counts = new TreeMap<String, Long>();
            var oresByDepth = new TreeMap<String, Map<Integer, Long>>();
            var interiorAirByDepth = new TreeMap<Integer, Long>();
            var fluidsByDepth = new TreeMap<String, Map<Integer, Long>>();
            long comparedNoiseBlocks = 0;
            // Standalone ProtoChunks cannot advance to surface/features on C2ME workers while inspected.
            if (sky != null) for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                var chunk = new ProtoChunk(new ChunkPos((location.getX() >> 4) + dx, (location.getZ() >> 4) + dz),
                        UpgradeData.EMPTY, level, server.registryAccess().registryOrThrow(Registries.BIOME), null);
                sky.fillFromNoise(Blender.empty(), level.getChunkSource().randomState(), level.structureManager(), chunk).join();
                int bx = chunk.getPos().getMinBlockX(), bz = chunk.getPos().getMinBlockZ();
                for (int x = bx; x < bx + 16; x++) for (int z = bz; z < bz + 16; z++) {
                    var expected = sky.getBaseColumn(x, z, level, level.getChunkSource().randomState());
                    for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y++) {
                        var state = chunk.getBlockState(mutable.set(x, y, z));
                        check(state == expected.getBlock(y), "Noise/base-column mismatch at " + mutable + ": " + state + " / " + expected.getBlock(y));
                        comparedNoiseBlocks++;
                    }
                }
            }
            sample.addProperty("comparedNoiseBlocks", comparedNoiseBlocks);
            long blocks = 0, nonAir = 0; int minSolidY = Integer.MAX_VALUE, maxSolidY = Integer.MIN_VALUE;
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                var chunk = level.getChunk((location.getX() >> 4) + dx, (location.getZ() >> 4) + dz);
                int bx = chunk.getPos().getMinBlockX(), bz = chunk.getPos().getMinBlockZ();
                for (int x = bx; x < bx + 16; x++) for (int z = bz; z < bz + 16; z++) {
                    GeologicalFrame frame = field == null ? null : ((GeologicalField)(Object)field).skyweave$originFrameAt(x, z, sky.resolvedSkyIslandSettings());
                    var predicted = sky == null ? null : sky.getBaseColumn(x, z, level, level.getChunkSource().randomState());
                    for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y++) {
                        var state = chunk.getBlockState(mutable.set(x, y, z)); blocks++;
                        int relative = frame == null ? y : frame.toReference(y);
                        if (state.isAir()) {
                            if (predicted != null && !predicted.getBlock(y).isAir()) {
                                interiorAirByDepth.merge(Math.floorDiv(relative, 16) * 16, 1L, Long::sum);
                            }
                            continue;
                        }
                        nonAir++; minSolidY = Math.min(minSolidY, y); maxSolidY = Math.max(maxSolidY, y);
                        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(); counts.merge(id, 1L, Long::sum);
                        if (id.endsWith("_ore")) {
                            oresByDepth.computeIfAbsent(id, unused -> new TreeMap<>()).merge(Math.floorDiv(relative, 16) * 16, 1L, Long::sum);
                        }
                        if (state.is(Blocks.WATER) || state.is(Blocks.LAVA)) {
                            fluidsByDepth.computeIfAbsent(id, unused -> new TreeMap<>()).merge(Math.floorDiv(relative, 16) * 16, 1L, Long::sum);
                        }

                    }
                }
            }
            sample.addProperty("chunks", 9); sample.addProperty("blocksVisited", blocks); sample.addProperty("nonAir", nonAir);
            sample.addProperty("minSolidY", minSolidY); sample.addProperty("maxSolidY", maxSolidY);
            sample.add("blocks", new Gson().toJsonTree(counts)); sample.add("oresByReferenceY", new Gson().toJsonTree(oresByDepth)); samples.add(sample);
            sample.add("interiorAirByReferenceY", new Gson().toJsonTree(interiorAirByDepth));
            sample.add("fluidsByReferenceY", new Gson().toJsonTree(fluidsByDepth));
            System.out.println("SKYWEAVE_GENERATED " + location + " nonAir=" + nonAir);
        }
        if (System.getProperty("neighbors.probe.generation").equals("adapted")) {
            report.add("climate", ClimateProbe.run(level));
            if (OptionalIntegrations.loaded("sable")) report.add("atmosphere", AtmosphereProbe.run(level));
            var integrations = new JsonObject();
            integrations.addProperty("skyVillages", OptionalIntegrations.skyVillages());
            for (String id : List.of("sable", "aeronautics_delivery_quests", "create", "createpropulsion", "aeronautics")) {
                integrations.addProperty(id, OptionalIntegrations.loaded(id));
            }
            report.add("integrations", integrations);
        }
        report.addProperty("elapsedSeconds", (System.nanoTime() - start) / 1e9); report.add("samples", samples);
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }
}
