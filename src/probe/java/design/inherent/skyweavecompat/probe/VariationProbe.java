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
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;
import org.sathrek.sky_archipelago.worldgen.generator.field.model.IslandDescriptor;
import java.nio.file.*;
import java.util.*;

/** Focused real sampler/fill/feature fixture, with explicit rare-biome/profile inputs. */
final class VariationProbe {
    static void run(MinecraftServer server, Path output) throws Exception {
        long began = System.nanoTime();
        var level = server.overworld();
        var generator = (SkyIslandChunkGenerator)level.getChunkSource().getGenerator();
        var settings = generator.resolvedSkyIslandSettings();
        var random = level.getChunkSource().randomState();
        var field = ((GeologicalGenerator)(Object)generator).skyweave$field(random);
        var styles = ((StyledTerrain)(Object)field).skyweave$styles();
        var kind = IslandStyles.Kind.valueOf(System.getProperty("neighbors.probe.variationKind", "FRACTURED"));
        IslandDescriptor island = null;
        search: for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) {
            var members = ((ClusterField)(Object)field).skyweave$members(x, z, settings);
            if (!members.isEmpty() && styles.style(members.getFirst()).kind() == kind) { island = members.getFirst(); break search; }
        }
        if (island == null) throw new AssertionError("No " + kind + " island in bounded explicit fixture");
        var otherGenerator = new SkyIslandChunkGenerator(generator.getBiomeSource(), generator.generatorSettings(), generator.embeddedSettings());
        var otherRandom = RandomState.create(generator.generatorSettings().value(),
                server.registryAccess().registryOrThrow(Registries.NOISE).asLookup(), level.getSeed());
        var other = ((GeologicalGenerator)(Object)otherGenerator).skyweave$field(otherRandom);
        var profile = ((ProfiledTerrain)(Object)field).skyweave$profile();
        var points = new ArrayList<BlockPos>();
        int solid = 0, empty = 0;
        for (int ix = -8; ix <= 8; ix++) for (int iz = -8; iz <= 8; iz++) {
            int x = island.centerX() + ix * island.radiusX() / 8, z = island.centerZ() + iz * island.radiusZ() / 8;
            var columns = ((InteriorField)(Object)field).skyweave$exteriorAt(x, z, settings);
            if (columns.isEmpty()) { empty++; continue; }
            solid++;
            for (var column : columns) if (column.bottomY() < GenerationProfile.MIN_Y + profile.floorClearance()
                    || column.topY() > GenerationProfile.MAX_Y - 1 - profile.buildingHeadroom())
                throw new AssertionError("Fractured exterior exceeds vertical envelope");
            if (points.size() < 2 || ix == 0 && iz == 0) points.add(new BlockPos(x, columns.getFirst().topY(), z));
        }
        if (solid < 20 || empty < 10) throw new AssertionError("Missing usable remnants or gaps");
        var rows = new JsonArray(); long compared = 0;
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
                    for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y++) {
                        if (proto.getBlockState(pos.set(bx, y, bz)) != base.getBlock(y))
                            throw new AssertionError("Fractured fill/base-column mismatch at " + pos);
                        compared++;
                    }
                }
            var chunk = level.getChunk(chunkPos.x, chunkPos.z);
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
            row.add("surfaceBlocks", new Gson().toJsonTree(blocks)); row.add("surfaceBiomes", new Gson().toJsonTree(biomes)); rows.add(row);
        }
        var report = new JsonObject(); report.addProperty("complete", true); report.addProperty("kind", kind.name());
        report.add("island", new Gson().toJsonTree(island)); report.add("style", new Gson().toJsonTree(styles.style(island)));
        report.addProperty("solidSampleColumns", solid); report.addProperty("emptySampleColumns", empty);
        report.addProperty("comparedNoiseBlocks", compared); report.add("samples", rows);
        report.addProperty("elapsedSeconds", (System.nanoTime() - began) / 1e9);
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
    }
    private VariationProbe() {}
}
