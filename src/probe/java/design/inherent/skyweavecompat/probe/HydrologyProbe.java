package design.inherent.skyweavecompat.probe;

import com.AdamNew.dynamicwaters.Config;
import com.AdamNew.dynamicwaters.HydrologyManager;
import com.AdamNew.dynamicwaters.terrain.BasinTile;
import com.AdamNew.dynamicwaters.terrain.CoarseHeightField;
import com.google.gson.*;
import design.inherent.skyweavecompat.terrain.*;
import net.minecraft.server.MinecraftServer;
import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;
import org.sathrek.sky_archipelago.worldgen.generator.field.model.IslandFamily;
import java.nio.file.*;
import java.util.*;

/** Native upstream drainage against the owned field, with no chunk requests in planning. */
final class HydrologyProbe {
    private static Path tickOutput;
    private static JsonObject tickReport;
    private static int remaining, elapsed;

    static void start(MinecraftServer server, Path output, int ticks) throws Exception {
        run(server, output);
        if (ticks == 0) { server.halt(false); return; }
        tickOutput = output; tickReport = JsonParser.parseString(Files.readString(output)).getAsJsonObject();
        tickReport.addProperty("complete", false); remaining = ticks; elapsed = 0;
        for (String key : List.of("generated", "generatedLakes", "outlets")) for (var entry : tickReport.getAsJsonArray(key)) {
            var site = entry.getAsJsonObject(); int cx = Math.floorDiv(site.get("x").getAsInt(), 16), cz = Math.floorDiv(site.get("z").getAsInt(), 16);
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) server.overworld().setChunkForced(cx + dx, cz + dz, true);
        }
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(tickReport) + "\n");
    }

    static void tick(MinecraftServer server) {
        if (remaining == 0) return;
        elapsed++;
        if (--remaining != 0) return;
        try {
            var level = server.overworld();
            for (String key : List.of("generated", "generatedLakes")) for (var entry : tickReport.getAsJsonArray(key)) {
                var site = entry.getAsJsonObject();
                var pos = new net.minecraft.core.BlockPos(site.get("x").getAsInt(), site.get("waterY").getAsInt(), site.get("z").getAsInt());
                if (!level.getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER)) throw new AssertionError("Water lost after fluid ticks: " + pos);
            }
            int falls = 0;
            for (var entry : tickReport.getAsJsonArray("outlets")) {
                var site = entry.getAsJsonObject();
                var pos = new net.minecraft.core.BlockPos(site.get("fallX").getAsInt(), site.get("waterY").getAsInt() - 4, site.get("fallZ").getAsInt());
                var state = level.getFluidState(pos);
                site.addProperty("afterTicks", level.getBlockState(pos).toString());
                if (state.is(net.minecraft.tags.FluidTags.WATER) && !state.isSource()) falls++;
            }
            if (tickReport.getAsJsonArray("outlets").isEmpty() || falls == 0) throw new AssertionError("No naturally flowing island outlet: " + tickReport.get("outlets"));
            tickReport.addProperty("fluidTicks", elapsed); tickReport.addProperty("flowingOutlets", falls);
            tickReport.addProperty("complete", true);
        } catch (Throwable error) { tickReport.addProperty("error", error.toString()); error.printStackTrace(); }
        try { Files.writeString(tickOutput, new GsonBuilder().setPrettyPrinting().create().toJson(tickReport) + "\n"); }
        catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
        server.halt(false);
    }
    static void run(MinecraftServer server, Path output) throws Exception {
        var level = server.overworld();
        var generator = (SkyIslandChunkGenerator)level.getChunkSource().getGenerator();
        var random = level.getChunkSource().randomState();
        var settings = generator.resolvedSkyIslandSettings();
        var field = ((GeologicalGenerator)(Object)generator).skyweave$field(random);
        if (settings.rivers().enabled() || settings.rivers().lakesEnabled()) throw new AssertionError("Native hydrology is active");
        if (Config.dynamicNormalRivers()) throw new AssertionError("Continental lowland network is active");
        var anchor = field.collectIslandPreviewsInRadius(0, 0, 8000, settings).stream()
                .filter(p -> p.family() == IslandFamily.ANCHOR_PLATEAU && !p.archetype().waterBased())
                .min(Comparator.comparingDouble(p -> Math.hypot(p.x(), p.z()))).orElseThrow();
        int before = level.getChunkSource().getLoadedChunksCount();
        long began = System.nanoTime();
        int solid = 0, empty = 0;
        for (int x = anchor.x() - 512; x <= anchor.x() + 512; x += 64)
            for (int z = anchor.z() - 512; z <= anchor.z() + 512; z += 64) {
                var col = ((InteriorField)(Object)field).skyweave$planningColumn(x, z, settings, TerrainStage.EXTERIOR);
                int expected = col.segments().isEmpty() || col.dominantDescriptor() != null && col.dominantDescriptor().archetype().waterBased()
                        ? generator.getMinY() : col.segments().getFirst().topY() + 1;
                int sampled = CoarseHeightField.surfaceAt(x, z, generator, random);
                int cached = CoarseHeightField.sample(Math.floorDiv(x, 8), Math.floorDiv(z, 8), generator, random);
                int gridX = Math.floorDiv(x, 8) * 8, gridZ = Math.floorDiv(z, 8) * 8;
                if (sampled != expected || cached != CoarseHeightField.surfaceAt(gridX, gridZ, generator, random))
                    throw new AssertionError("Watershed reads a different terrain at " + x + "," + z);
                if (expected == generator.getMinY()) empty++; else solid++;
            }
        var tile = BasinTile.build(Math.floorDiv(anchor.x(), 1024), Math.floorDiv(anchor.z(), 1024), generator, random,
                level.getSeed(), generator.getSeaLevel(), BasinTile.channelThreshold(Config.MOUNTAIN_RIVER_DENSITY.get()), true);
        int paths = tile.pathsByRegion.values().stream().mapToInt(List::size).sum();
        int lakes = tile.lakesByRegion.values().stream().mapToInt(List::size).sum();
        var witnesses = new JsonArray();
        int supportedNodes = 0, voidNodes = 0;
        var carvingWitnesses = new JsonArray();
        var outlets = new JsonArray();
        var boundaryDiagnostics = new JsonArray();
        int outletChecks = 0;
        for (var region : tile.pathsByRegion.values()) for (var path : region) {
            int supported = 0, voids = 0, minimumY = Integer.MAX_VALUE, maximumY = Integer.MIN_VALUE;
            int deepestIncision = 0;
            for (int i = 0; i < path.validNodeCount; i++) {
                int ground = CoarseHeightField.surfaceAt((int)Math.floor(path.segmentsX[i]), (int)Math.floor(path.segmentsZ[i]), generator, random);
                if (ground == generator.getMinY()) voids++; else supported++;
                if (ground != generator.getMinY() && boundaryDiagnostics.size() < 12
                        && (i > 0 && CoarseHeightField.surfaceAt((int)Math.floor(path.segmentsX[i - 1]), (int)Math.floor(path.segmentsZ[i - 1]), generator, random) == generator.getMinY()
                        || i + 1 < path.validNodeCount && CoarseHeightField.surfaceAt((int)Math.floor(path.segmentsX[i + 1]), (int)Math.floor(path.segmentsZ[i + 1]), generator, random) == generator.getMinY())) {
                    int x = (int)Math.floor(path.segmentsX[i]), z = (int)Math.floor(path.segmentsZ[i]);
                    var raw = ((InteriorField)(Object)field).skyweave$planningColumn(x, z, settings, TerrainStage.INTERIOR);
                    var cut = HydrologyManager.calculateRiverCarving(x, z, ground - 1, generator.getSeaLevel(), generator, level, random);
                    var row = new JsonObject(); row.addProperty("x", x); row.addProperty("z", z); row.addProperty("ground", ground);
                    row.addProperty("pathY", path.segmentsY[i]); row.addProperty("targetBed", cut.targetY); row.addProperty("targetWater", cut.waterLevel);
                    row.addProperty("modifies", cut.modifies); row.addProperty("roof", cut.cavernRoofY);
                    row.addProperty("acceptedWet", field.sampleHydrologyColumn(x, z, settings).wet());
                    if (!raw.segments().isEmpty()) {
                        row.addProperty("solidBottom", raw.segments().getFirst().bottomY()); row.addProperty("solidTop", raw.segments().getFirst().topY());
                    }
                    boundaryDiagnostics.add(row);
                }
                if (outlets.isEmpty() && ground != generator.getMinY() && outletChecks < 10000) {
                    int px = (int)Math.floor(path.segmentsX[i]), pz = (int)Math.floor(path.segmentsZ[i]);
                    outletSearch: for (int ox = -8; ox <= 8; ox += 2) for (int oz = -8; oz <= 8; oz += 2) {
                        outletChecks++;
                        int x = px + ox, z = pz + oz;
                        var river = field.sampleHydrologyColumn(x, z, settings);
                        if (!river.wet() || river.lake()) continue;
                        for (var direction : net.minecraft.core.Direction.Plane.HORIZONTAL)
                            for (int reach = 1; reach <= 7; reach++) {
                                int nx = x + direction.getStepX() * reach, nz = z + direction.getStepZ() * reach;
                                var segments = field.sampleSolidSegments(nx, nz, settings);
                                if (segments.stream().anyMatch(segment -> segment.contains(river.fluidSurfaceY()))) break;
                                if (!segments.isEmpty() || field.sampleHydrologyColumn(nx, nz, settings).containsFluidAt(river.fluidSurfaceY())) continue;
                                var site = new JsonObject(); site.addProperty("x", x); site.addProperty("z", z);
                                site.addProperty("waterY", river.fluidSurfaceY()); site.addProperty("fallX", nx); site.addProperty("fallZ", nz);
                                site.addProperty("clearReach", reach);
                                outlets.add(site); break outletSearch;
                            }
                    }
                }
                if (ground != generator.getMinY()) deepestIncision = Math.max(deepestIncision, ground - path.segmentsY[i]);
                if (ground != generator.getMinY() && carvingWitnesses.size() < 12 && i % 4 == 0) {
                    int x = (int)Math.floor(path.segmentsX[i]), z = (int)Math.floor(path.segmentsZ[i]);
                    var cut = HydrologyManager.calculateRiverCarving(x, z, ground - 1, generator.getSeaLevel(), generator, level, random);
                    var sample = new JsonObject();
                    sample.addProperty("x", x); sample.addProperty("z", z); sample.addProperty("ground", ground);
                    sample.addProperty("bed", cut.targetY); sample.addProperty("water", cut.waterLevel);
                    sample.addProperty("modifies", cut.modifies); sample.addProperty("mountain", cut.isMountainRiver);
                    sample.addProperty("lake", cut.isLake); sample.addProperty("wall", cut.isWatershedWall);
                    carvingWitnesses.add(sample);
                }
                minimumY = Math.min(minimumY, path.segmentsY[i]); maximumY = Math.max(maximumY, path.segmentsY[i]);
            }
            supportedNodes += supported; voidNodes += voids;
            if (witnesses.size() >= 8 || path.validNodeCount == 0) continue;
            var row = new JsonObject(); row.addProperty("nodes", path.validNodeCount);
            row.addProperty("supportedNodes", supported); row.addProperty("voidNodes", voids);
            row.addProperty("minimumY", minimumY); row.addProperty("maximumY", maximumY);
            row.addProperty("deepestIncision", deepestIncision);
            row.addProperty("x", path.segmentsX[0]); row.addProperty("y", path.segmentsY[0]); row.addProperty("z", path.segmentsZ[0]);
            witnesses.add(row);
        }
        int after = level.getChunkSource().getLoadedChunksCount();
        if (solid == 0 || empty == 0 || before != after) throw new AssertionError("Invalid bounded planning control");
        for (var entry : outlets) {
            var site = entry.getAsJsonObject();
            var pos = new net.minecraft.core.BlockPos(site.get("fallX").getAsInt(), site.get("waterY").getAsInt() - 4, site.get("fallZ").getAsInt());
            for (var status : List.of(net.minecraft.world.level.chunk.status.ChunkStatus.NOISE,
                    net.minecraft.world.level.chunk.status.ChunkStatus.SURFACE, net.minecraft.world.level.chunk.status.ChunkStatus.CARVERS)) {
                var chunk = level.getChunk(Math.floorDiv(pos.getX(), 16), Math.floorDiv(pos.getZ(), 16), status);
                site.addProperty("at_" + status.toString(), chunk.getBlockState(pos).toString());
                site.addProperty("status_" + status.toString(), chunk.getPersistedStatus().toString());
            }
        }
        var generated = new JsonArray();
        int moving = 0;
        for (var entry : carvingWitnesses) {
            var point = entry.getAsJsonObject();
            int x = point.get("x").getAsInt(), z = point.get("z").getAsInt();
            var river = field.sampleHydrologyColumn(x, z, settings);
            if (!river.wet()) continue;
            var pos = new net.minecraft.core.BlockPos(x, river.fluidSurfaceY(), z);
            var chunk = level.getChunkAt(pos);
            var water = chunk.getBlockState(pos);
            var floor = chunk.getBlockState(new net.minecraft.core.BlockPos(x, river.solidSurfaceY(), z));
            if (!water.getFluidState().is(net.minecraft.tags.FluidTags.WATER) || floor.isAir()
                    || !floor.getFluidState().isEmpty())
                throw new AssertionError("Native watershed column was not filled at " + pos + ": " + floor + "/" + water);
            var predicted = generator.getBaseColumn(x, z, level, random);
            if (!predicted.getBlock(pos.getY()).getFluidState().is(net.minecraft.tags.FluidTags.WATER))
                throw new AssertionError("Base column lost a generated river's fluid at " + pos);
            if (generator.getBaseHeight(x, z, net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG, level, random) != pos.getY() + 1)
                throw new AssertionError("Surface height lost a generated river's fluid at " + pos);
            var row = new JsonObject(); row.addProperty("x", x); row.addProperty("z", z);
            row.addProperty("bed", river.solidSurfaceY()); row.addProperty("waterY", river.fluidSurfaceY());
            row.addProperty("fluid", water.toString()); row.addProperty("floor", floor.toString());
            var flow = water.getFluidState().getFlow(level, pos);
            row.addProperty("flow", flow.toString());
            row.addProperty("upstreamFlow", HydrologyManager.getRiverFlow(x, pos.getY(), z, level).toString());
            row.addProperty("biome", level.getBiome(pos).getRegisteredName());
            row.addProperty("sameGenerator", HydrologyManager.getGlobalGenerator() == generator);
            row.addProperty("baseColumnAndHeightAgree", true);
            if (flow.lengthSqr() > .0001) moving++;
            generated.add(row);
            if (generated.size() == 3) break;
        }
        if (generated.isEmpty()) throw new AssertionError("No actual watershed channel was generated");
        if (moving == 0) throw new AssertionError("Native watershed channel has no flow: " + generated);
        var report = new JsonObject(); report.addProperty("complete", true);
        report.addProperty("stage", "upstream watershed planning, native solid/fluid column fill and server fluid flow");
        report.addProperty("seed", level.getSeed()); report.addProperty("anchorX", anchor.x()); report.addProperty("anchorZ", anchor.z());
        report.addProperty("solidSamples", solid); report.addProperty("emptySamples", empty);
        report.addProperty("paths", paths); report.addProperty("lakes", lakes); report.add("witnesses", witnesses);
        report.add("carving", carvingWitnesses);
        report.add("generated", generated);
        report.add("outlets", outlets); report.addProperty("outletMetadataChecks", outletChecks);
        report.add("boundaryDiagnostics", boundaryDiagnostics);
        var owner = field.sampleDominantIslandDescriptor(anchor.x(), anchor.z(), settings);
        report.addProperty("anchorInterior", owner == null ? "none" : IslandInteriors.kind(owner, InteriorConfiguration.profile()).name());
        for (var entry : outlets) {
            var site = entry.getAsJsonObject(); int x = site.get("x").getAsInt(), z = site.get("z").getAsInt(), y = site.get("waterY").getAsInt();
            var pos = new net.minecraft.core.BlockPos(x, y, z);
            if (!level.getChunkAt(pos).getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER)) throw new AssertionError("Outlet source was not filled: " + pos);
            var fall = new net.minecraft.core.BlockPos(site.get("fallX").getAsInt(), y - 4, site.get("fallZ").getAsInt());
            site.addProperty("beforeTicks", level.getChunkAt(fall).getBlockState(fall).toString());
        }
        if (net.neoforged.fml.ModList.get().isLoaded("sable")) {
            var site = generated.get(0).getAsJsonObject();
            report.add("sable", HydrologySableProbe.observe(level, new net.minecraft.core.BlockPos(
                    site.get("x").getAsInt(), site.get("waterY").getAsInt(), site.get("z").getAsInt())));
        }
        var lakeSearch = new JsonArray();
        var generatedLakes = new JsonArray();
        var lakeHosts = field.collectIslandPreviewsInRadius(0, 0, 16000, settings).stream()
                .filter(p -> p.family() == IslandFamily.ANCHOR_PLATEAU && p.archetype()
                        == org.sathrek.sky_archipelago.worldgen.generator.field.IslandShapeArchetype.BOWL_CRATER)
                .sorted(Comparator.comparingDouble(p -> Math.hypot(p.x(), p.z()))).limit(4).toList();
        for (var host : lakeHosts) {
            var basin = BasinTile.build(Math.floorDiv(host.x(), 1024), Math.floorDiv(host.z(), 1024), generator, random,
                    level.getSeed(), generator.getSeaLevel(), BasinTile.channelThreshold(Config.MOUNTAIN_RIVER_DENSITY.get()), true);
            var row = new JsonObject(); row.addProperty("x", host.x()); row.addProperty("z", host.z());
            row.addProperty("lakes", basin.lakesByRegion.values().stream().mapToInt(List::size).sum());
            lakeSearch.add(row);
            if (generatedLakes.size() == 0) search:
                for (var region : basin.lakesByRegion.values()) for (var lake : region)
                    for (int cx = 0; cx < lake.cellsW; cx++) for (int cz = 0; cz < lake.cellsH; cz++) {
                        int x = (lake.minCellX + cx) * 8 + 4, z = (lake.minCellZ + cz) * 8 + 4;
                        if (!lake.inside(x + .5, z + .5)) continue;
                        var river = field.sampleHydrologyColumn(x, z, settings);
                        if (!river.lake()) continue;
                        var pos = new net.minecraft.core.BlockPos(x, river.fluidSurfaceY(), z);
                        var chunk = level.getChunkAt(pos);
                        if (!chunk.getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER))
                            throw new AssertionError("Accepted watershed lake was not filled at " + pos);
                        var site = new JsonObject(); site.addProperty("x", x); site.addProperty("z", z);
                        site.addProperty("waterY", river.fluidSurfaceY()); site.addProperty("bed", river.solidSurfaceY());
                        site.addProperty("fluid", chunk.getBlockState(pos).toString());
                        generatedLakes.add(site);
                        break search;
                    }
        }
        report.add("lakeSearch", lakeSearch);
        report.add("generatedLakes", generatedLakes);
        if (generatedLakes.isEmpty()) throw new AssertionError("No actual watershed lake was generated: " + lakeSearch);
        report.addProperty("supportedNodes", supportedNodes); report.addProperty("voidNodes", voidNodes);
        report.addProperty("loadedChunksBefore", before); report.addProperty("loadedChunksAfter", after);
        report.addProperty("elapsedSeconds", (System.nanoTime() - began) / 1e9);
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
    }
}
