package design.inherent.skyweavecompat.probe;

import com.google.gson.*;
import design.inherent.skyweavecompat.terrain.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandShapeArchetype;
import org.sathrek.sky_archipelago.worldgen.generator.field.internal.IslandShapeSampler;
import org.sathrek.sky_archipelago.worldgen.generator.field.model.IslandFamily;

/** Exact added-remnant witnesses through metadata, columns, base queries and native noise fill. */
final class FringeProbe {
    static void run(MinecraftServer server, Path output) throws Exception {
        var level = server.overworld();
        var generator = (SkyIslandChunkGenerator)level.getChunkSource().getGenerator();
        var random = level.getChunkSource().randomState();
        var settings = generator.resolvedSkyIslandSettings();
        var field = ((GeologicalGenerator)(Object)generator).skyweave$field(random);
        var member = field.getClass().getDeclaredField("shapeSampler"); member.setAccessible(true);
        var sampler = (IslandShapeSampler)member.get(field);
        var previews = field.collectIslandPreviewsInRadius(0, 0, 16000, settings).stream()
                .filter(p -> p.family() == IslandFamily.ANCHOR_PLATEAU && !p.archetype().waterBased())
                .sorted(Comparator.comparingDouble(p -> Math.hypot(p.x(), p.z()))).toList();
        var witnesses = new JsonArray(); var profiles = new HashSet<Boolean>();
        var archetypes = EnumSet.noneOf(IslandShapeArchetype.class);
        var required = EnumSet.of(IslandShapeArchetype.CLASSIC, IslandShapeArchetype.CRESCENT,
                IslandShapeArchetype.TERRACE, IslandShapeArchetype.BOWL_CRATER);
        boolean ellipse = false;
        int before = level.getChunkSource().getLoadedChunksCount(), checked = 0;
        for (var preview : previews) {
            var plan = preview.terrainPlan(); var island = plan.descriptor();
            boolean disintegrating = FragmentFringes.disintegrating(island.seed(), ShapeConfiguration.profile().fringe());
            if (profiles.contains(disintegrating) && archetypes.contains(island.archetype()) && ellipse) continue;
            var bounds = plan.bounds();
            boolean found = false;
            for (int x = bounds.minX(); x <= bounds.maxX() && !found; x += 8)
                for (int z = bounds.minZ(); z <= bounds.maxZ() && !found; z += 8) {
                    var horizontal = sampler.sampleHorizontal(plan, x, z);
                    var fragment = ((FringeColumn)(Object)horizontal).skyweave$fragment();
                    if (fragment == null || fragment.depth() < 80) continue;
                    int top = island.centerY() + island.plateauHeight() + fragment.topOffset();
                    int bottom = top - fragment.depth() + 1;
                    if (bottom < GenerationProfile.MIN_Y + GenerationConfiguration.profile().floorClearance()
                            || top >= GenerationProfile.MAX_Y - GenerationConfiguration.profile().buildingHeadroom())
                        throw new AssertionError("Fringe exceeds the reserved envelope");
                    int middle = Math.floorDiv(bottom + top, 2);
                    var column = ((InteriorField)(Object)field).skyweave$planningColumn(x, z, settings, TerrainStage.EXTERIOR);
                    if (!island.equals(column.dominantDescriptor())) continue;
                    if (column.segments().stream().noneMatch(segment -> segment.contains(middle)))
                        throw new AssertionError("Prepared remnant is absent from actual column");
                    var actual = generator.getBaseColumn(x, z, level, random);
                    if (actual.getBlock(middle).isAir() || field.sampleDensity(x, middle, z, settings) <= 0)
                        throw new AssertionError("Base column or density lost a remnant");
                    var row = new JsonObject(); row.addProperty("x", x); row.addProperty("z", z);
                    row.addProperty("bottom", bottom); row.addProperty("top", top);
                    row.addProperty("middle", middle); row.addProperty("disintegrating", disintegrating);
                    row.addProperty("archetype", island.archetype().name());
                    row.addProperty("radiusX", island.radiusX()); row.addProperty("radiusZ", island.radiusZ());
                    row.addProperty("firstFree", generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, random));
                    witnesses.add(row); profiles.add(disintegrating); archetypes.add(island.archetype());
                    ellipse |= Math.max(island.radiusX(), island.radiusZ()) > Math.min(island.radiusX(), island.radiusZ()) * 1.05;
                    found = true;
                }
            checked++;
            if (profiles.size() == 2 && archetypes.containsAll(required) && ellipse) break;
        }
        int after = level.getChunkSource().getLoadedChunksCount();
        if (before != after || profiles.size() != 2 || !archetypes.containsAll(required) || !ellipse)
            throw new AssertionError("Missing bounded perimeter controls: " + archetypes + "/" + profiles + "/ellipse=" + ellipse);
        for (var entry : witnesses) {
            var row = entry.getAsJsonObject();
            int x = row.get("x").getAsInt(), z = row.get("z").getAsInt(), y = row.get("middle").getAsInt();
            var chunk = level.getChunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16), ChunkStatus.NOISE);
            if (chunk.getBlockState(new BlockPos(x, y, z)).isAir()) throw new AssertionError("Native fill lost a planned fringe");
            row.addProperty("nativeFilled", true);
        }
        var report = new JsonObject(); report.addProperty("complete", true); report.addProperty("seed", level.getSeed());
        report.addProperty("plansChecked", checked); report.addProperty("ellipseVerified", ellipse); report.addProperty("loadedChunksBefore", before);
        report.addProperty("loadedChunksAfterMetadata", after); report.add("witnesses", witnesses);
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
    }
}
