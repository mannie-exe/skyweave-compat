package design.inherent.skyweavecompat.probe;

import org.sathrek.sky_archipelago.worldgen.generator.field.model.IslandDescriptor;
import org.sathrek.sky_archipelago.worldgen.generator.field.model.IslandFamily;
import com.google.gson.*;
import design.inherent.skyweavecompat.terrain.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;
import java.nio.file.*;
import java.util.*;

/** Stratified generated-chunk samples; no extrapolated value is an observed inventory. */
final class IslandSurveyProbe {
    private IslandSurveyProbe() {}

    static void run(MinecraftServer server, Path output) throws Exception {
        var level = server.overworld();
        var generator = (SkyIslandChunkGenerator)level.getChunkSource().getGenerator();
        TerrainProbe.checkEmbeddedSettings(generator);
        var field = ((GeologicalGenerator)(Object)generator).skyweave$field(level.getChunkSource().randomState());
        var settings = generator.resolvedSkyIslandSettings();
        var spawn = level.getSharedSpawnPos();
        var descriptors = field.collectIslandPreviewsInRadius(spawn.getX(), spawn.getZ(), 10000, settings);
        var anchors = descriptors.stream().filter(p -> p.family() == IslandFamily.ANCHOR_PLATEAU)
                .sorted(Comparator.comparingDouble(p -> Math.hypot(p.x() - spawn.getX(), p.z() - spawn.getZ()))).toList();
        var report = new JsonObject();
        report.addProperty("schema", 1); report.addProperty("kind", "skyweave-island-survey");
        report.addProperty("seed", level.getSeed()); report.addProperty("preset", GenerationProfile.ID);
        report.addProperty("referenceSurfaceY", GeologicalFrame.REFERENCE_SURFACE);
        report.addProperty("method", "One deterministic random chunk per 7x7 spatial stratum, within twice the anchor descriptor radius; only columns dominated by that anchor contribute to estimates");
        report.addProperty("limitations", "Estimates are bounded to the sampled square and may miss rare features. Counts do not establish visibility, mining access, complete island inventories or survival travel. Structures are disabled. Companion columns are excluded from anchor resource estimates.");
        report.add("descriptors", new Gson().toJsonTree(descriptors));
        var spawnRow = new JsonObject(); spawnRow.addProperty("x", spawn.getX()); spawnRow.addProperty("y", spawn.getY()); spawnRow.addProperty("z", spawn.getZ());
        spawnRow.addProperty("support", BuiltInRegistries.BLOCK.getKey(level.getBlockState(spawn.below()).getBlock()).toString());
        spawnRow.addProperty("feetBlock", BuiltInRegistries.BLOCK.getKey(level.getBlockState(spawn).getBlock()).toString());
        spawnRow.addProperty("feetClear", level.getBlockState(spawn).getCollisionShape(level, spawn).isEmpty());
        spawnRow.addProperty("headClear", level.getBlockState(spawn.above()).getCollisionShape(level, spawn.above()).isEmpty());
        spawnRow.addProperty("supported", level.getBlockState(spawn.below()).isFaceSturdy(level, spawn.below(), net.minecraft.core.Direction.UP));
        report.add("spawn", spawnRow);
        var islands = new JsonArray(); long began = System.nanoTime();
        var pos = new BlockPos.MutableBlockPos();
        for (var anchor : anchors.stream().limit(3).toList()) {
            var island = new JsonObject(); island.add("descriptor", new Gson().toJsonTree(anchor));
            int cellX = Math.floorDiv(anchor.x(), ((ProfiledTerrain)(Object)field).skyweave$profile().spacing());
            int cellZ = Math.floorDiv(anchor.z(), ((ProfiledTerrain)(Object)field).skyweave$profile().spacing());
            boolean grouped = false;
            for (int dx = -1; dx <= 1 && !grouped; dx++) for (int dz = -1; dz <= 1 && !grouped; dz++) {
                var members = ((ClusterField)(Object)field).skyweave$members(cellX + dx, cellZ + dz, settings);
                if (members.isEmpty()) continue;
                var head = members.getFirst();
                if (head.centerX() != anchor.x() || head.centerZ() != anchor.z()) continue;
                island.add("companionDescriptors", new Gson().toJsonTree(members.subList(1, members.size())));
                island.add("geologicalFrame", new Gson().toJsonTree(IslandGeometry.geologicalFrame(head)));
                grouped = true;
            }
            if (!grouped) throw new AssertionError("Missing native companion group");
            var nearest = anchors.stream().filter(p -> p != anchor)
                    .min(Comparator.comparingDouble(p -> Math.hypot(p.x() - anchor.x(), p.z() - anchor.z()))).orElseThrow();
            island.addProperty("nearestCenterDistance", Math.hypot(nearest.x() - anchor.x(), nearest.z() - anchor.z()));
            island.addProperty("descriptorEdgeGap", Math.hypot(nearest.x() - anchor.x(), nearest.z() - anchor.z()) - anchor.radius() - nearest.radius());
            int steps = (int)Math.ceil(Math.hypot(nearest.x() - anchor.x(), nearest.z() - anchor.z()) / 8);
            double stepLength = Math.hypot(nearest.x() - anchor.x(), nearest.z() - anchor.z()) / steps;
            int voidSamples = 0, longestVoid = 0, landSegments = 0; boolean previousLand = false;
            for (int step = 0; step <= steps; step++) {
                int x = (int)Math.round(anchor.x() + (nearest.x() - anchor.x()) * (double)step / steps);
                int z = (int)Math.round(anchor.z() + (nearest.z() - anchor.z()) * (double)step / steps);
                boolean land = field.sampleColumn(x, z, settings).exists();
                if (land && !previousLand) landSegments++;
                if (land) voidSamples = 0; else longestVoid = Math.max(longestVoid, ++voidSamples);
                previousLand = land;
            }
            var route = new JsonObject(); route.addProperty("stepBlocks", stepLength);
            route.addProperty("longestVoidGapBlocks", longestVoid * stepLength);
            route.addProperty("landSegments", landSegments);
            route.addProperty("evidence", "Sampled horizontal center-line density; not a navigated or altitude-safe route");
            island.add("straightRoute", route);
            int minX = (anchor.x() - 2 * anchor.radius()) >> 4, maxX = (anchor.x() + 2 * anchor.radius()) >> 4;
            int minZ = (anchor.z() - 2 * anchor.radius()) >> 4, maxZ = (anchor.z() + 2 * anchor.radius()) >> 4;
            var bounds = new JsonArray(); for (int n : new int[]{minX, minZ, maxX, maxZ}) bounds.add(n);
            island.add("chunkBounds", bounds);
            var counts = new TreeMap<String, Long>(); var estimates = new TreeMap<String, Double>();
            var oreDepths = new TreeMap<String, Map<Integer, Long>>(); var samples = new JsonArray();
            var exposureChecked = new TreeMap<String, Long>(); var exposedOres = new TreeMap<String, Long>();
            var biomeColumns = new TreeMap<String, Integer>();
            var openings = new JsonArray(); int openingColumns = 0;
            var random = new Random(level.getSeed() ^ ((long)anchor.x() << 32) ^ anchor.z());
            int minSolid = Integer.MAX_VALUE, maxSolid = Integer.MIN_VALUE; long observedColumns = 0;
            long islandBegan = System.nanoTime();
            for (int sx = 0; sx < 7; sx++) for (int sz = 0; sz < 7; sz++) {
                int x0 = minX + (maxX - minX + 1) * sx / 7, x1 = minX + (maxX - minX + 1) * (sx + 1) / 7;
                int z0 = minZ + (maxZ - minZ + 1) * sz / 7, z1 = minZ + (maxZ - minZ + 1) * (sz + 1) / 7;
                if (x1 <= x0 || z1 <= z0) continue;
                int cx = random.nextInt(x0, x1), cz = random.nextInt(z0, z1);
                int weight = (x1 - x0) * (z1 - z0);
                var chunk = level.getChunk(cx, cz); var row = new JsonObject();
                row.addProperty("x", cx); row.addProperty("z", cz); row.addProperty("representedChunks", weight);
                var chunkCounts = new TreeMap<String, Long>(); int columns = 0;
                for (int x = cx * 16; x < cx * 16 + 16; x++) for (int z = cz * 16; z < cz * 16 + 16; z++) {
                    var owner = field.sampleDominantIslandDescriptor(x, z, settings);
                    if (owner == null || owner.family() != IslandFamily.ANCHOR_PLATEAU
                            || owner.centerX() != anchor.x() || owner.centerZ() != anchor.z()) continue;
                    columns++; observedColumns++;
                    var frame = ((GeologicalField)(Object)field).skyweave$originFrameAt(x, z, settings);
                    int surface = field.sampleColumnProfile(x, z, settings).topY();
                    biomeColumns.merge(level.getBiome(pos.set(x, surface, z)).getRegisteredName(), 1, Integer::sum);
                    // Actual post-feature air cut into predicted terrain, not a density-only cave claim.
                    int openDepth = 0;
                    while (openDepth < 64 && surface - openDepth >= level.getMinBuildHeight()
                            && chunk.getBlockState(pos.set(x, surface - openDepth, z)).isAir()) openDepth++;
                    if (openDepth >= 8) {
                        var predicted = generator.getBaseColumn(x, z, level, level.getChunkSource().randomState());
                        int removed = 0;
                        for (int dy = 0; dy < openDepth; dy++) if (!predicted.getBlock(surface - dy).isAir()) removed++;
                        if (removed >= 8) {
                            openingColumns++;
                            if (openings.size() < 8) {
                                var opening = new JsonObject();
                                opening.addProperty("x", x); opening.addProperty("z", z);
                                opening.addProperty("surfaceY", surface); opening.addProperty("airDepth", openDepth);
                                openings.add(opening);
                            }
                        }
                    }
                    for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y++) {
                        var state = chunk.getBlockState(pos.set(x, y, z)); if (state.isAir()) continue;
                        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                        chunkCounts.merge(id, 1L, Long::sum); counts.merge(id, 1L, Long::sum);
                        estimates.merge(id, (double)weight, Double::sum);
                        minSolid = Math.min(minSolid, y); maxSolid = Math.max(maxSolid, y);
                        if (id.endsWith("_ore") || id.equals("minecraft:raw_iron_block") || id.equals("minecraft:raw_copper_block")) {
                            oreDepths.computeIfAbsent(id, unused -> new TreeMap<>())
                                .merge(Math.floorDiv(frame.toReference(y), 16) * 16, 1L, Long::sum);
                            // Inspect only same-chunk neighbors; never generate another chunk to
                            // decide whether an ore face is exposed. Count blocks, not faces.
                            if ((x & 15) > 0 && (x & 15) < 15 && (z & 15) > 0 && (z & 15) < 15
                                    && y > level.getMinBuildHeight() && y < level.getMaxBuildHeight() - 1) {
                                exposureChecked.merge(id, 1L, Long::sum);
                                for (var direction : net.minecraft.core.Direction.values()) {
                                    var neighbor = chunk.getBlockState(pos.set(x + direction.getStepX(),
                                            y + direction.getStepY(), z + direction.getStepZ()));
                                    if (neighbor.isAir() || !neighbor.getFluidState().isEmpty()) {
                                        exposedOres.merge(id, 1L, Long::sum); break;
                                    }
                                }
                            }
                        }
                    }
                }
                row.addProperty("anchorColumns", columns); row.add("blocks", new Gson().toJsonTree(chunkCounts)); samples.add(row);
            }
            island.addProperty("observedAnchorColumns", observedColumns);
            island.addProperty("surfaceOpeningColumns", openingColumns);
            island.add("surfaceOpeningExamples", openings);
            island.addProperty("surfaceOpeningEvidence", "Generated air at least eight blocks into predicted surface rock; does not prove walkable access or deep connectivity");
            if (observedColumns > 0) {
                island.addProperty("observedMinY", minSolid); island.addProperty("observedMaxY", maxSolid);
                island.addProperty("observedHeadroom", level.getMaxBuildHeight() - 1 - maxSolid);
            }
            island.add("observedBlocks", new Gson().toJsonTree(counts)); island.add("estimatedBlocks", new Gson().toJsonTree(estimates));
            island.add("observedOresByReferenceY", new Gson().toJsonTree(oreDepths));
            island.add("exposureCheckedOres", new Gson().toJsonTree(exposureChecked));
            island.add("exposedOres", new Gson().toJsonTree(exposedOres));
            island.addProperty("oreExposureEvidence", "Observed ore blocks adjacent to air or fluid; excludes chunk-edge columns and does not establish player access");
            island.add("sampledSurfaceBiomeColumns", new Gson().toJsonTree(biomeColumns)); island.add("samples", samples);
            island.addProperty("elapsedSeconds", (System.nanoTime() - islandBegan) / 1e9); islands.add(island);
            System.out.println("SKYWEAVE_ISLAND " + anchor.x() + "," + anchor.z() + " columns=" + observedColumns + " blocks=" + counts.values().stream().mapToLong(Long::longValue).sum());
        }
        report.add("islands", islands); report.addProperty("elapsedSeconds", (System.nanoTime() - began) / 1e9);
        var memory = java.lang.management.ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        report.addProperty("heapUsedAtEndBytes", memory.getUsed()); report.addProperty("heapCommittedAtEndBytes", memory.getCommitted());
        report.addProperty("complete", islands.size() == 3 && spawnRow.get("supported").getAsBoolean()
                && spawnRow.get("feetClear").getAsBoolean() && spawnRow.get("headClear").getAsBoolean());
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }
}
