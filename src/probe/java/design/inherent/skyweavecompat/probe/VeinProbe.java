package design.inherent.skyweavecompat.probe;

import org.sathrek.sky_archipelago.worldgen.generator.field.model.IslandFamily;
import com.google.gson.*;
import design.inherent.skyweavecompat.terrain.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;
import java.nio.file.*;
import java.util.*;

/** Find rare material witnesses, then require native fill and query agreement at those points. */
final class VeinProbe {
    private VeinProbe() {}
    static void run(MinecraftServer server, Path output) throws Exception {
        var level = server.overworld();
        var generator = (SkyIslandChunkGenerator)level.getChunkSource().getGenerator();
        var random = level.getChunkSource().randomState();
        var field = ((GeologicalGenerator)(Object)generator).skyweave$field(random);
        var settings = generator.resolvedSkyIslandSettings();
        var veins = ((VeinField)(Object)field).skyweave$veins();
        var found = new LinkedHashMap<String, BlockPos>();
        var materials = new TreeMap<String, Long>();
        var sampledDepths = new TreeMap<Integer, Long>();
        var ironChunks = new LinkedHashSet<ChunkPos>();
        long sampledBlocks = 0, began = System.nanoTime();
        var anchors = field.collectIslandPreviewsInRadius(0, 0, 24000, settings).stream()
                .filter(p -> p.family() == IslandFamily.ANCHOR_PLATEAU)
                .sorted(Comparator.comparingDouble(p -> Math.hypot(p.x(), p.z()))).limit(32).toList();
        search: for (var anchor : anchors)
            for (int x = anchor.x() - anchor.radius(); x <= anchor.x() + anchor.radius(); x += 32)
                for (int z = anchor.z() - anchor.radius(); z <= anchor.z() + anchor.radius(); z += 32) {
                    var frame = ((GeologicalField)(Object)field).skyweave$originFrameAt(x, z, settings);
                    if (frame == null) continue;
                    for (var segment : field.sampleSolidSegments(x, z, settings))
                        for (int y = segment.bottomY(); y <= segment.topY(); y++) {
                            sampledBlocks++;
                            sampledDepths.merge(Math.floorDiv(frame.toReference(y), 16) * 16, 1L, Long::sum);
                            var state = veins.sample(x, y, z, frame);
                            if (state != null) materials.merge(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(), 1L, Long::sum);
                            if (state != null && state.is(Blocks.DEEPSLATE_IRON_ORE) && ironChunks.size() < 12)
                                ironChunks.add(new ChunkPos(x >> 4, z >> 4));
                            if (state != null && (state.is(Blocks.RAW_IRON_BLOCK) || state.is(Blocks.RAW_COPPER_BLOCK)))
                                found.putIfAbsent(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(), new BlockPos(x, y, z));
                            if (found.size() == 2) break search;
                        }
                }
        // Coarse columns can miss the native 2% raw-block roll. Inspect complete noise-filled
        // chunks around witnessed iron veins instead of repeatedly widening a sparse grid.
        int targetedChunks = 0;
        if (!found.containsKey("minecraft:raw_iron_block")) {
            var mutable = new BlockPos.MutableBlockPos();
            dense: for (var location : ironChunks) {
                var chunk = new ProtoChunk(location, UpgradeData.EMPTY, level,
                        server.registryAccess().registryOrThrow(Registries.BIOME), null);
                generator.fillFromNoise(Blender.empty(), random, level.structureManager(), chunk).join();
                targetedChunks++;
                for (int x = location.getMinBlockX(); x <= location.getMaxBlockX(); x++)
                    for (int z = location.getMinBlockZ(); z <= location.getMaxBlockZ(); z++)
                        for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y++)
                            if (chunk.getBlockState(mutable.set(x, y, z)).is(Blocks.RAW_IRON_BLOCK)) {
                                found.put("minecraft:raw_iron_block", mutable.immutable());
                                break dense;
                            }
            }
        }
        var rows = new JsonArray();
        for (var entry : found.entrySet()) {
            var pos = entry.getValue();
            var chunk = new ProtoChunk(new ChunkPos(pos), UpgradeData.EMPTY, level,
                    server.registryAccess().registryOrThrow(Registries.BIOME), null);
            generator.fillFromNoise(Blender.empty(), random, level.structureManager(), chunk).join();
            var actual = chunk.getBlockState(pos);
            if (!BuiltInRegistries.BLOCK.getKey(actual.getBlock()).toString().equals(entry.getKey())
                    || actual != generator.getBaseColumn(pos.getX(), pos.getZ(), level, random).getBlock(pos.getY()))
                throw new AssertionError("Raw vein witness differs in native fill/query");
            var row = new JsonObject(); row.addProperty("material", entry.getKey());
            row.add("position", new Gson().toJsonTree(pos));
            row.addProperty("referenceY", ((GeologicalField)(Object)field)
                    .skyweave$originFrameAt(pos.getX(), pos.getZ(), settings).toReference(pos.getY()));
            row.addProperty("completedChunkMaterial", BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString());
            rows.add(row);
        }
        var report = new JsonObject(); report.addProperty("seed", Long.toString(level.getSeed()));
        report.addProperty("evidence", "targeted raw-vein witnesses; not an unbiased abundance estimate");
        report.addProperty("sampledSolidBlocks", sampledBlocks); report.add("samples", rows);
        report.addProperty("targetedIronChunks", targetedChunks);
        report.add("sampledReferenceDepths", new Gson().toJsonTree(sampledDepths));
        report.add("materialCounts", new Gson().toJsonTree(materials));
        report.addProperty("complete", found.size() == 2);
        report.addProperty("elapsedSeconds", (System.nanoTime() - began) / 1e9);
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
    }
}
