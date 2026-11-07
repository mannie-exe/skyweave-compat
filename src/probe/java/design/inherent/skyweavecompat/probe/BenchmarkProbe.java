package design.inherent.skyweavecompat.probe;

import org.sathrek.sky_archipelago.worldgen.generator.field.model.IslandFamily;
import com.google.gson.*;
import design.inherent.skyweavecompat.terrain.GeologicalGenerator;
import java.nio.file.*;
import java.util.*;
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
        long started = System.nanoTime();
        var anchors = field.collectIslandPreviewsInRadius(0, 0, 16000, settings).stream()
                .filter(p -> p.family() == IslandFamily.ANCHOR_PLATEAU)
                .sorted(Comparator.comparingDouble(p -> Math.hypot(p.x(), p.z()))).toList();
        long columns = 0, solidSamples = 0;
        for (var anchor : anchors.stream().limit(Math.clamp(Integer.getInteger("neighbors.probe.samples", 12), 1, 12)).toList()) {
            for (int dx = -800; dx <= 800; dx += 16) for (int dz = -800; dz <= 800; dz += 16) {
                if (field.sampleColumn(anchor.x() + dx, anchor.z() + dz, settings).exists()) solidSamples++;
                columns++;
            }
        }
        double columnSeconds = (System.nanoTime() - started) / 1e9;
        long generationStarted = System.nanoTime();
        for (var anchor : anchors.stream().limit(2).toList()) {
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                level.getChunk((anchor.x() >> 4) + dx, (anchor.z() >> 4) + dz);
            }
        }
        var report = new JsonObject(); report.addProperty("seed", level.getSeed());
        report.addProperty("structuresEnabled", server.getWorldData().worldGenOptions().generateStructures());
        report.addProperty("columns", columns); report.addProperty("occupiedColumnSamples", solidSamples);
        report.addProperty("columnSeconds", columnSeconds);
        report.addProperty("chunksRequested", 18);
        report.addProperty("chunkSeconds", (System.nanoTime() - generationStarted) / 1e9);
        report.addProperty("heapUsedBytesAfter", Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());
        report.addProperty("complete", anchors.size() >= 12);
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }
}
