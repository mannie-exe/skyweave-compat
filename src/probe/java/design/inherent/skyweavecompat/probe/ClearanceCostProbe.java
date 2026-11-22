package design.inherent.skyweavecompat.probe;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import design.inherent.skyweavecompat.placement.IslandTerrain;
import design.inherent.skyweavecompat.placement.NeighborRule;
import design.inherent.skyweavecompat.terrain.GeologicalGenerator;
import design.inherent.skyweavecompat.terrain.InteriorConfiguration;
import design.inherent.skyweavecompat.terrain.InteriorField;
import design.inherent.skyweavecompat.terrain.ProfiledTerrain;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.server.MinecraftServer;
import org.sathrek.sky_archipelago.config.SkyIslandSettings;
import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;
import org.sathrek.sky_archipelago.worldgen.generator.field.model.BiomeTerrainSampler;
import org.sathrek.sky_archipelago.worldgen.generator.field.model.IslandFamily;

/** Same public rule/native field seam as the retained pre-change artifact, with fresh per-case caches. */
final class ClearanceCostProbe {
    static void run(MinecraftServer server, Path output) throws Exception {
        if (!Boolean.getBoolean("neighbors.probe.clearanceJfr")) {
            measure(server, output);
            return;
        }
        try (var recording = new jdk.jfr.Recording()) {
            recording.enable("jdk.ObjectAllocationSample").withStackTrace();
            recording.enable("jdk.ExecutionSample").withPeriod(java.time.Duration.ofMillis(5));
            recording.start();
            measure(server, output);
            recording.stop();
            recording.dump(output.resolveSibling(output.getFileName() + ".jfr"));
        }
    }

    private static void measure(MinecraftServer server, Path output) throws Exception {
        var level = server.overworld();
        var generator = (SkyIslandChunkGenerator) level.getChunkSource().getGenerator();
        var settings = generator.resolvedSkyIslandSettings();
        var original = ((GeologicalGenerator)(Object)generator).skyweave$field(level.getChunkSource().randomState());
        var samplerField = IslandField.class.getDeclaredField("biomeTerrainSampler");
        samplerField.setAccessible(true);
        var sampler = (BiomeTerrainSampler) samplerField.get(original);
        var anchor = original.collectIslandPreviewsInRadius(0, 0, 8000, settings).stream()
                .filter(island -> island.family() == IslandFamily.ANCHOR_PLATEAU)
                .min(java.util.Comparator.comparingDouble(island -> Math.hypot(island.x(), island.z()))).orElseThrow();
        var report = new JsonObject();
        report.addProperty("complete", false);
        report.addProperty("scope", "Replay of the final native support-rule consumer on real exterior planning data; not whole-structure assembly or tick-duration evidence");
        report.addProperty("thread", Thread.currentThread().getName());
        report.addProperty("seed", level.getSeed());
        report.add("anchor", new com.google.gson.Gson().toJsonTree(anchor));
        var rows = new JsonArray(); report.add("cases", rows);
        int loaded = level.getChunkSource().getLoadedChunksCount();
        var survey = new IslandTerrain(original, settings, null);
        int edge = anchor.x();
        for (int x = anchor.x() + 1600; x >= anchor.x(); x--)
            if (survey.exists(x, anchor.z()) && survey.islandRadius(x, anchor.z()) >= 96) { edge = x; break; }
        report.addProperty("qualifiedEastEdge", edge);
        var allocations = ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean bean
                && bean.isThreadAllocatedMemorySupported() ? bean : null;
        if (allocations != null && !allocations.isThreadAllocatedMemoryEnabled()) allocations.setThreadAllocatedMemoryEnabled(true);
        for (int[] gaps : new int[][]{{64, 128}, {64, 2048}, {256, 512}, {2048, 2048}}) for (int width : new int[]{1, 16, 64}) {
            int gap = gaps[0], maximum = gaps[1];
            var policy = new NeighborRule.Settings(true, gap, maximum, 96);
            for (String position : java.util.List.of("inside", "edge", "far")) {
                int x = switch (position) { case "inside" -> anchor.x(); case "edge" -> edge + gap + 8; default -> edge + maximum + 1024; };
                var footprint = new NeighborRule.Footprint(x, anchor.z() - width / 2, x + width - 1, anchor.z() - width / 2 + width - 1);
                var field = new IslandField(original.layoutSeed(), sampler);
                ((ProfiledTerrain)(Object)field).skyweave$configure(((ProfiledTerrain)(Object)original).skyweave$profile());
                ((InteriorField)(Object)field).skyweave$configureInteriors(level.getChunkSource().randomState(), InteriorConfiguration.profile());
                var nativeTerrain = new IslandTerrain(field, settings, null);
                String coldDecision = null;
                for (int repeat = 0; repeat < 2; repeat++) {
                    var terrain = new Counted(nativeTerrain);
                    var row = new JsonObject(); rows.add(row);
                    row.addProperty("minimumGap", gap); row.addProperty("maximumGap", maximum);
                    row.addProperty("width", width); row.addProperty("position", position); row.addProperty("repeat", repeat);
                    row.add("footprint", new com.google.gson.Gson().toJsonTree(footprint));
                    long beforeBytes = allocations == null ? -1 : allocations.getThreadAllocatedBytes(Thread.currentThread().threadId());
                    long started = System.nanoTime();
                    var decision = NeighborRule.evaluate(footprint, policy, terrain);
                    row.addProperty("nanos", System.nanoTime() - started);
                    if (allocations != null) row.addProperty("threadAllocatedBytes", allocations.getThreadAllocatedBytes(Thread.currentThread().threadId()) - beforeBytes);
                    row.addProperty("existsCalls", terrain.exists); row.addProperty("radiusCalls", terrain.radii);
                    row.addProperty("decision", decision.name());
                    row.addProperty("hydrologyStats", field.hydrologyCacheStats().toString());
                    requireNoHydrology(field);
                    if (repeat == 0) coldDecision = decision.name();
                    else if (!coldDecision.equals(decision.name())) throw new AssertionError("Warm query changed the native decision");
                    Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report));
                }
            }
        }
        // Positive cases are found through the actual policy, not inferred from a descriptor
        // radius. Fringes and nearby layers can make an apparent geometric gap unsuitable.
        int positives = 0;
        var defaults = new NeighborRule.Settings(true, 64, 128, 96);
        find: for (int angle = 0; angle < 32; angle++) for (int radius = 240; radius <= 900; radius += 16) {
            double direction = angle * Math.PI / 16;
            int x = anchor.x() + (int)Math.round(Math.cos(direction) * radius);
            int z = anchor.z() + (int)Math.round(Math.sin(direction) * radius);
            var footprint = new NeighborRule.Footprint(x, z, x + 15, z + 15);
            if (NeighborRule.coarseDecision(footprint, defaults, survey) != NeighborRule.Decision.ACCEPTED
                    || NeighborRule.evaluate(footprint, defaults, survey) != NeighborRule.Decision.ACCEPTED) continue;
            var field = new IslandField(original.layoutSeed(), sampler);
            ((ProfiledTerrain)(Object)field).skyweave$configure(((ProfiledTerrain)(Object)original).skyweave$profile());
            ((InteriorField)(Object)field).skyweave$configureInteriors(level.getChunkSource().randomState(), InteriorConfiguration.profile());
            for (int repeat = 0; repeat < 2; repeat++) {
                var terrain = new Counted(new IslandTerrain(field, settings, null));
                long beforeBytes = allocations == null ? -1 : allocations.getThreadAllocatedBytes(Thread.currentThread().threadId());
                long started = System.nanoTime();
                var decision = NeighborRule.evaluate(footprint, defaults, terrain);
                var row = new JsonObject(); rows.add(row);
                row.addProperty("nanos", System.nanoTime() - started);
                if (allocations != null) row.addProperty("threadAllocatedBytes", allocations.getThreadAllocatedBytes(Thread.currentThread().threadId()) - beforeBytes);
                row.addProperty("minimumGap", 64); row.addProperty("maximumGap", 128); row.addProperty("width", 16);
                row.addProperty("position", "qualified-" + positives); row.addProperty("repeat", repeat);
                row.add("footprint", new com.google.gson.Gson().toJsonTree(footprint));
                row.addProperty("existsCalls", terrain.exists); row.addProperty("radiusCalls", terrain.radii);
                row.addProperty("decision", decision.name());
                row.addProperty("hydrologyStats", field.hydrologyCacheStats().toString()); requireNoHydrology(field);
                if (decision != NeighborRule.Decision.ACCEPTED) throw new AssertionError("Fresh field changed the qualified decision");
            }
            if (++positives == 2) break find;
        }
        if (positives < 2) throw new AssertionError("The native cost fixture did not qualify two accepted footprints");
        if (Boolean.getBoolean("neighbors.probe.clearanceOracle")) {
            int samples = 0, occupied = 0, waterSamples = 0;
            var previews = original.collectIslandPreviewsInRadius(0, 0, 16000, settings);
            var selected = new java.util.ArrayList<>(previews.stream().filter(island -> !island.archetype().waterBased()).limit(8).toList());
            selected.addAll(previews.stream().filter(island -> island.archetype().waterBased()).limit(4).toList());
            for (var island : selected) for (int dx = -6; dx <= 6; dx++) for (int dz = -6; dz <= 6; dz++) {
                int x = island.x() + (int)Math.round(dx * island.radius() / 5.0);
                int z = island.z() + (int)Math.round(dz * island.radius() / 5.0);
                boolean actual = survey.exists(x, z);
                boolean expected = ((InteriorField)(Object)original).skyweave$planningColumn(x, z, settings,
                        design.inherent.skyweavecompat.terrain.TerrainStage.EXTERIOR).profile().exists();
                if (actual != expected) throw new AssertionError("Exterior presence differs from native full column at " + x + "," + z);
                samples++; if (actual) occupied++; if (island.archetype().waterBased()) waterSamples++;
            }
            report.addProperty("oracleSamples", samples); report.addProperty("oracleOccupied", occupied);
            report.addProperty("oracleWetDescriptorSamples", waterSamples);
            if (occupied == 0 || occupied == samples || waterSamples == 0)
                throw new AssertionError("Presence oracle lacks occupied, empty, or wet descriptor witnesses");
        }
        report.addProperty("loadedChunksBefore", loaded);
        report.addProperty("loadedChunksAfter", level.getChunkSource().getLoadedChunksCount());
        if (loaded != level.getChunkSource().getLoadedChunksCount()) throw new AssertionError("Clearance loaded a chunk");
        report.addProperty("complete", true);
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }

    private static final class Counted implements NeighborRule.Terrain {
        final IslandTerrain nativeTerrain;
        long exists, radii;
        Counted(IslandTerrain terrain) { nativeTerrain = terrain; }
        public boolean exists(int x, int z) { exists++; return nativeTerrain.exists(x, z); }
        public int islandRadius(int x, int z) { radii++; return nativeTerrain.islandRadius(x, z); }
    }

    private static void requireNoHydrology(IslandField field) {
        var stats = field.hydrologyCacheStats();
        if (stats.entries() != 0 || stats.hits() != 0 || stats.misses() != 0 || stats.waits() != 0 || stats.lakePayloadBytes() != 0)
            throw new AssertionError("Exterior clearance invoked completed hydrology: " + stats);
    }
}
