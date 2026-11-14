package design.inherent.skyweavecompat.probe;

import org.sathrek.sky_archipelago.worldgen.generator.field.model.IslandFamily;
import com.google.gson.*;
import design.inherent.skyweavecompat.terrain.GeologicalGenerator;
import java.nio.file.*;
import java.util.*;
import java.security.MessageDigest;
import java.lang.management.ManagementFactory;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.server.MinecraftServer;
import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;

/** A fixed bounded workload using APIs shared with the previous release. Not a calibrated benchmark suite. */
final class BenchmarkProbe {
    private BenchmarkProbe() {}
    static void run(MinecraftServer server, Path output) throws Exception {
        var level = server.overworld();
        var generator = (SkyIslandChunkGenerator)level.getChunkSource().getGenerator();
        var field = ((GeologicalGenerator)(Object)generator).skyweave$field(level.getChunkSource().randomState());
        var settings = generator.resolvedSkyIslandSettings();
        var eligibility = PresetIdentityProbe.run(server, generator);
        var columnDigest = MessageDigest.getInstance("SHA-256");
        long started = System.nanoTime();
        var anchors = field.collectIslandPreviewsInRadius(0, 0, 16000, settings).stream()
                .filter(p -> p.family() == IslandFamily.ANCHOR_PLATEAU)
                .sorted(Comparator.comparingDouble(p -> Math.hypot(p.x(), p.z()))).toList();
        long columns = 0, solidSamples = 0;
        for (var anchor : anchors.stream().limit(Math.clamp(Integer.getInteger("neighbors.probe.samples", 12), 1, 12)).toList()) {
            for (int dx = -800; dx <= 800; dx += 16) for (int dz = -800; dz <= 800; dz += 16) {
                var column = field.sampleColumn(anchor.x() + dx, anchor.z() + dz, settings);
                if (column.exists()) solidSamples++;
                integer(columnDigest, column.bottomY()); integer(columnDigest, column.topY());
                columns++;
            }
        }
        double columnSeconds = (System.nanoTime() - started) / 1e9;
        long generationStarted = System.nanoTime();
        var chunks = new ArrayList<net.minecraft.world.level.chunk.LevelChunk>();
        for (var anchor : anchors.stream().limit(2).toList()) {
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                chunks.add(level.getChunk((anchor.x() >> 4) + dx, (anchor.z() >> 4) + dz));
            }
        }
        double chunkSeconds = (System.nanoTime() - generationStarted) / 1e9;
        var blocks = MessageDigest.getInstance("SHA-256");
        var biomes = MessageDigest.getInstance("SHA-256");
        var starts = new TreeSet<String>();
        long blockCount = 0;
        var biomeRegistry = level.registryAccess().registryOrThrow(Registries.BIOME);
        var structureRegistry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        for (var chunk : chunks) {
            integer(blocks, chunk.getPos().x); integer(blocks, chunk.getPos().z);
            for (var section : chunk.getSections()) {
                for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                    integer(blocks, Block.getId(section.getBlockState(x, y, z))); blockCount++;
                }
                for (int y = 0; y < 4; y++) for (int z = 0; z < 4; z++) for (int x = 0; x < 4; x++)
                    integer(biomes, biomeRegistry.getId(section.getBiomes().get(x, y, z).value()));
            }
            chunk.getAllStarts().forEach((structure, start) -> {
                if (start.isValid()) starts.add(structureRegistry.getKey(structure) + ":" + start.getChunkPos()
                        + ":" + start.getBoundingBox() + ":" + start.getPieces().size());
            });
        }
        var report = new JsonObject(); report.addProperty("seed", level.getSeed());
        var memory = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        report.addProperty("heapInitialBytes", memory.getInit());
        report.addProperty("heapCommittedBytesAfter", memory.getCommitted());
        report.addProperty("heapMaximumBytes", memory.getMax());
        var collectors = new JsonArray();
        for (var collector : ManagementFactory.getGarbageCollectorMXBeans()) {
            var row = new JsonObject(); row.addProperty("name", collector.getName());
            row.addProperty("collections", collector.getCollectionCount());
            row.addProperty("collectionMillis", collector.getCollectionTime()); collectors.add(row);
        }
        report.add("collectors", collectors);
        var pools = new JsonArray();
        for (var pool : ManagementFactory.getMemoryPoolMXBeans()) {
            var usage = pool.getUsage(); var collected = pool.getCollectionUsage();
            var row = new JsonObject(); row.addProperty("name", pool.getName());
            row.addProperty("kind", pool.getType().toString());
            if (usage != null) row.addProperty("usedBytesAtObservation", usage.getUsed());
            if (collected != null) row.addProperty("usedBytesAfterLastCollection", collected.getUsed());
            pools.add(row);
        }
        report.add("memoryPools", pools);
        report.add("eligibility", eligibility);
        report.addProperty("columnDigest", HexFormat.of().formatHex(columnDigest.digest()));
        report.addProperty("blockDigest", HexFormat.of().formatHex(blocks.digest()));
        report.addProperty("biomeDigest", HexFormat.of().formatHex(biomes.digest()));
        report.addProperty("physicalBlocksCompared", blockCount);
        report.add("acceptedStarts", new Gson().toJsonTree(starts));
        report.addProperty("structuresEnabled", server.getWorldData().worldGenOptions().generateStructures());
        report.addProperty("columns", columns); report.addProperty("occupiedColumnSamples", solidSamples);
        report.addProperty("columnSeconds", columnSeconds);
        report.addProperty("chunksRequested", 18);
        report.addProperty("chunkSeconds", chunkSeconds);
        report.addProperty("heapUsedBytesAfter", Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());
        report.addProperty("complete", anchors.size() >= 12);
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }

    private static void integer(MessageDigest digest, int value) {
        digest.update((byte)(value >>> 24)); digest.update((byte)(value >>> 16));
        digest.update((byte)(value >>> 8)); digest.update((byte)value);
    }
}
