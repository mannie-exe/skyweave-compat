package design.inherent.skyweavecompat.probe;

import com.google.gson.*;
import design.inherent.skyweavecompat.terrain.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.sathrek.sky_archipelago.config.SkyIslandSettings;
import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;
import org.sathrek.sky_archipelago.worldgen.generator.field.TerrainColumn;
import java.nio.file.*;
import java.util.*;

/** Bounded native fluid/material and tick checks in disposable probe worlds. */
final class WaterProbe {
    private enum Kind { RIVER, LAKE, NATIVE, COASTAL, PERIMETER }
    private record Site(BlockPos source, BlockPos edge, int bedY, Kind mode) {}
    private static final List<Site> SITES = new ArrayList<>();
    private static JsonObject report;
    private static Path destination;
    private static int remaining, elapsed;
    private static long began;
    private WaterProbe() {}

    static List<TerrainColumn> columns(IslandField field, int x, int z, SkyIslandSettings settings) {
        var result = new ArrayList<TerrainColumn>();
        var ocean = field.sampleWaterArchetypeColumnProfile(x, z, settings);
        if (ocean.water()) result.add(new TerrainColumn(ocean.solidSurfaceY() + 1, ocean.fluidSurfaceY()));
        var river = field.sampleHydrologyColumn(x, z, settings);
        if (river.wet()) result.add(new TerrainColumn(river.fluidBottomY(), river.fluidSurfaceY()));
        return List.copyOf(result);
    }

    static boolean start(MinecraftServer server, Path output, int ticks) throws Exception {
        began = System.nanoTime(); destination = output; elapsed = 0; SITES.clear();
        var level = server.overworld();
        var generator = (SkyIslandChunkGenerator)level.getChunkSource().getGenerator();
        var random = level.getChunkSource().randomState();
        var field = ((GeologicalGenerator)(Object)generator).skyweave$field(random);
        var settings = generator.resolvedSkyIslandSettings();
        report = new JsonObject(); report.addProperty("seed", level.getSeed());
        report.add("oceanProfile", new Gson().toJsonTree(OceanConfiguration.profile()));
        report.add("rivers", new Gson().toJsonTree(RiverConfiguration.settings()));
        var selected = EnumSet.noneOf(Kind.class);
        int probes = 0;
        int horizontalSamples = 0;
        int radius = Integer.getInteger("neighbors.probe.waterRadius", 8192);
        boolean oceans = Boolean.getBoolean("neighbors.probe.oceans");
        if (oceans) report.add("oceanSpires", oceanSpires(field, settings));
        var samplerField = IslandField.class.getDeclaredField("shapeSampler");
        samplerField.setAccessible(true);
        var shapes = (org.sathrek.sky_archipelago.worldgen.generator.field.internal.IslandShapeSampler)samplerField.get(field);
        if (oceans) report.add("oceanGeometry", OceanGeometryProbe.inspect(level, generator, field, shapes, settings, radius));
        if (!oceans) {
            // Native plans provide bounded witnesses; do not scan every void column.
            riverSearch: for (var preview : field.collectIslandPreviewsInRadius(0,0,radius,settings)) {
                if (preview.terrainPlan() == null || preview.archetype().waterBased()) continue;
                var plan = field.hydrologyPlanFor(preview.terrainPlan().descriptor(),settings);
                var points = new ArrayList<BlockPos>();
                for (var lake : plan.lakes()) points.add(new BlockPos(lake.centerX(),lake.waterSurfaceY(),lake.centerZ()));
                for (var reach : plan.reaches()) for (var point : reach.points())
                    points.add(new BlockPos(point.x(),point.waterSurfaceY(),point.z()));
                for (var point : points) {
                    if (++probes > 4096) break riverSearch;
                    var river = field.sampleHydrologyColumn(point.getX(),point.getZ(),settings);
                    if (!river.wet()) continue;
                    var kind = river.lake() ? Kind.LAKE : Kind.RIVER;
                    if (selected.add(kind)) SITES.add(new Site(point.atY(river.fluidSurfaceY()),null,river.solidSurfaceY(),kind));
                    if (selected.containsAll(EnumSet.of(Kind.RIVER,Kind.LAKE))) break riverSearch;
                }
            }
            if (!selected.containsAll(EnumSet.of(Kind.RIVER,Kind.LAKE)))
                throw new IllegalStateException("Native river/lake witnesses missing in bounded plan search: " + selected);
        }
        var oceanDiagnostics = new JsonArray(); report.add("oceanDiagnostics", oceanDiagnostics);
        if (oceans)
        for (var preview : field.collectIslandPreviewsInRadius(0, 0, radius, settings)) {
            if (SITES.size() >= 3) break;
            if (!OceanIslands.supportsShorelineTreatment(preview.archetype())) continue;
            var island = preview.terrainPlan() == null ? field.sampleDominantIslandDescriptor(preview.x(), preview.z(), settings)
                    : preview.terrainPlan().descriptor();
            if (island == null) continue;
            var mode = Kind.valueOf(OceanConfiguration.profile().mode(island.seed()).name());
            if (selected.contains(mode)) continue;
            int reach = GenerationProfile.supportRadius(island.maxRadius());
            Site choice = null; int wetSamples = 0, openNeighbors = 0;
            // Dense enough to find narrow cuts, with an explicit total query budget.
            int step = mode == Kind.COASTAL ? 2 : Math.max(2, island.maxRadius() / 20);
            search: for (int dx = -reach; dx <= reach; dx += step)
                for (int dz = -reach; dz <= reach; dz += step) {
                    if (++probes > 100000) break search;
                    int x = island.centerX() + dx, z = island.centerZ() + dz;
                    if (mode == Kind.COASTAL && preview.terrainPlan() != null) {
                        if (++horizontalSamples > 2000000) break search;
                        var horizontal = shapes.sampleHorizontal(preview.terrainPlan(),x,z);
                        if (!horizontal.inInfluence() || OceanIslands.coveOpening(island,
                                horizontal.localX(), horizontal.localZ(), OceanConfiguration.profile()) < .99) {
                            probes--; continue;
                        }
                    }
                    var water = field.sampleWaterArchetypeColumnProfile(x, z, settings);
                    if (!water.water()) continue;
                    wetSamples++;
                    var source = new BlockPos(x, water.fluidSurfaceY(), z);
                    if (mode == Kind.NATIVE) { choice = new Site(source, null, water.solidSurfaceY(), mode); break search; }
                    for (var direction : Direction.Plane.HORIZONTAL) {
                        var edge = source.relative(direction);
                        if (columns(field, edge.getX(), edge.getZ(), settings).stream().anyMatch(c -> c.contains(edge.getY()))) continue;
                        openNeighbors++;
                        if (field.sampleSolidSegments(edge.getX(), edge.getZ(), settings).stream()
                                .noneMatch(c -> c.intersectsInclusive(edge.getY()-16,edge.getY()))) {
                            choice = new Site(source, edge, water.solidSurfaceY(), mode); break search;
                        }
                    }
                }
            var diagnostic = new JsonObject(); diagnostic.addProperty("x", island.centerX()); diagnostic.addProperty("z", island.centerZ());
            diagnostic.addProperty("mode", mode.name()); diagnostic.addProperty("wetSamples", wetSamples);
            diagnostic.addProperty("openNeighbors", openNeighbors); diagnostic.addProperty("planningQueries", probes);
            diagnostic.addProperty("horizontalSamples", horizontalSamples); diagnostic.addProperty("found", choice != null);
            oceanDiagnostics.add(diagnostic);
            if (choice != null) { SITES.add(choice); selected.add(mode); }
            if (probes > 100000) break;
        }
        report.addProperty("planningQueries", probes);
        report.addProperty("horizontalSamples",horizontalSamples);
        if (oceans) {
            var profile = OceanConfiguration.profile();
            var required = EnumSet.noneOf(Kind.class);
            if (profile.lagoonWeight() > 0) {
                if (profile.coastalChance() + profile.perimeterChance() < 1) required.add(Kind.NATIVE);
                if (profile.coastalChance() > 0) required.add(Kind.COASTAL);
                if (profile.perimeterChance() > 0) required.add(Kind.PERIMETER);
            }
            if (!selected.containsAll(required)) {
                report.addProperty("complete", false);
                Files.writeString(destination, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
                throw new IllegalStateException("Missing native wet-mode witnesses: " + required + "; found " + selected);
            }
        }
        if (SITES.isEmpty() && (!oceans || OceanConfiguration.profile().lagoonWeight() > 0))
            throw new IllegalStateException("No wet native fixture site within bounded search; select an ocean-biome fixture");
        report.addProperty("wetChecksApplicable", !SITES.isEmpty());
        var observations = new JsonArray(); report.add("observations", observations);
        for (var site : SITES) {
            var center = new ChunkPos(site.source());
            for (int x = center.x - 1; x <= center.x + 1; x++) for (int z = center.z - 1; z <= center.z + 1; z++) {
                level.getChunk(x, z); level.setChunkForced(x, z, true);
            }
            var base = generator.getBaseColumn(site.source().getX(), site.source().getZ(), level, random);
            if (base.getBlock(site.source().getY()).getFluidState().isEmpty())
                throw new AssertionError("Native base column lost planned fluid at " + site.source());
        }
        observe(level);
        remaining = Math.max(0, ticks);
        if (remaining == 0) { finish(); return false; }
        return true;
    }

    private static JsonArray oceanSpires(IslandField field, SkyIslandSettings settings) {
        var rows = new JsonArray();
        var membership = (ClusterField)(Object)field;
        for (int cx = -4; cx <= 4; cx++) for (int cz = -4; cz <= 4; cz++) {
            var members = membership.skyweave$members(cx, cz, settings);
            if (members.isEmpty() || !members.getFirst().archetype().waterBased()) continue;
            for (var member : members) {
                if (member.family() == org.sathrek.sky_archipelago.worldgen.generator.field.model.IslandFamily.SATELLITE)
                    throw new AssertionError("Ocean cluster acquired an ordinary land satellite");
                if (member.family() != org.sathrek.sky_archipelago.worldgen.generator.field.model.IslandFamily.SPIRE) continue;
                if (member.archetype().waterBased()) throw new AssertionError("Ocean needle kept a wet archetype");
                var point = new BlockPos(member.centerX(), member.centerY(), member.centerZ());
                var solid = field.sampleSolidSegments(point.getX(), point.getZ(), settings);
                if (solid.isEmpty()) continue; // A native narrow shape need not cover its nominal center.
                if (!columns(field, point.getX(), point.getZ(), settings).isEmpty())
                    throw new AssertionError("Ocean spire center contains planned fluid");
                var frame = ((GeologicalField)(Object)field).skyweave$originFrameAt(point.getX(), point.getZ(), settings);
                if (!IslandGeometry.geologicalFrame(members.getFirst()).equals(frame))
                    throw new AssertionError("Ocean spire received an independent geological frame");
                var row = new JsonObject(); row.addProperty("anchorType", members.getFirst().archetype().name());
                row.add("anchor", new Gson().toJsonTree(members.getFirst()));
                row.add("spire", new Gson().toJsonTree(member)); row.add("solid", new Gson().toJsonTree(solid)); rows.add(row);
            }
        }
        if (OceanConfiguration.profile().spireCompanions() && rows.isEmpty())
            throw new AssertionError("No solid ocean spire in the bounded explicit ocean fixture");
        if (!OceanConfiguration.profile().spireCompanions() && !rows.isEmpty())
            throw new AssertionError("Disabled ocean spire companions remained present");
        return rows;
    }

    static void tick(MinecraftServer server) {
        if (remaining <= 0) return;
        remaining--; elapsed++;
        if (remaining != 0) return;
        try { observe(server.overworld()); finish(); }
        catch (Throwable failure) {
            report.addProperty("complete", false); report.addProperty("failure", failure.toString());
            try { finish(); } catch (Exception writing) { writing.printStackTrace(); }
            failure.printStackTrace();
        } finally { server.halt(false); }
    }
    private static void observe(ServerLevel level) {
        var row = new JsonObject(); row.addProperty("tick", elapsed);
        var sites = new JsonArray();
        for (var site : SITES) {
            var value = new JsonObject(); value.addProperty("mode", site.mode().name());
            value.addProperty("source", site.source().toShortString());
            value.addProperty("sourceFluid", !level.getFluidState(site.source()).isEmpty());
            value.addProperty("frozenSource",level.getBlockState(site.source()).is(net.minecraft.world.level.block.Blocks.ICE)
                    && level.getBiome(site.source()).value().coldEnoughToSnow(site.source()));
            value.addProperty("sourceBlock", net.minecraft.core.registries.BuiltInRegistries.BLOCK
                    .getKey(level.getBlockState(site.source()).getBlock()).toString());
            var bed = site.source().atY(site.bedY());
            value.addProperty("bedY", site.bedY());
            value.addProperty("bedSupported",level.getBlockState(bed).isFaceSturdy(level,bed,Direction.UP));
            if (site.edge() != null) {
                int flowing = 0;
                for (int y = site.edge().getY(); y >= site.edge().getY() - 16; y--)
                    if (!level.getFluidState(site.edge().atY(y)).isEmpty()) flowing++;
                value.addProperty("edgeFluidBlocks", flowing);
            }
            sites.add(value);
        }
        row.add("sites", sites); report.getAsJsonArray("observations").add(row);
    }
    private static void finish() throws Exception {
        var rows = report.getAsJsonArray("observations");
        boolean valid = !report.has("failure");
        for (var value : rows.get(rows.size() - 1).getAsJsonObject().getAsJsonArray("sites")) {
            var site = value.getAsJsonObject();
            valid &= site.get("sourceFluid").getAsBoolean() || site.get("frozenSource").getAsBoolean();
            valid &= site.get("bedSupported").getAsBoolean();
            if (elapsed >= 100 && site.has("edgeFluidBlocks")) valid &= site.get("edgeFluidBlocks").getAsInt() >= 12;
        }
        report.addProperty("complete", valid);
        report.addProperty("fluidAndContainmentChecksPassed", valid && !SITES.isEmpty());
        report.addProperty("observedTicks", elapsed); report.addProperty("elapsedSeconds", (System.nanoTime() - began) / 1e9);
        Files.writeString(destination, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
    }
}
