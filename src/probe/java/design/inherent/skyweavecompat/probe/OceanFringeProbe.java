package design.inherent.skyweavecompat.probe;

import com.google.gson.*;
import design.inherent.skyweavecompat.terrain.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandShapeArchetype;
import org.sathrek.sky_archipelago.worldgen.generator.field.model.IslandFamily;

/** Real dry fragments through surface and features, selected from actual native ownership. */
final class OceanFringeProbe {
    static void run(MinecraftServer server, Path output) throws Exception {
        var level = server.overworld();
        var generator = (SkyIslandChunkGenerator)level.getChunkSource().getGenerator();
        var settings = generator.resolvedSkyIslandSettings();
        var field = ((GeologicalGenerator)(Object)generator).skyweave$field(level.getChunkSource().randomState());
        var interiors = (InteriorField)(Object)field;
        var styles = ((StyledTerrain)(Object)field).skyweave$styles();
        var found = new LinkedHashMap<String, JsonObject>();
        var treatments = EnumSet.noneOf(IslandComponents.Fringe.class);
        for (var preview : field.collectIslandPreviewsInRadius(0, 0, 32000, settings).stream()
                .filter(p -> p.family() == IslandFamily.ANCHOR_PLATEAU)
                .sorted(Comparator.comparingDouble(p -> Math.hypot(p.x(), p.z()))).toList()) {
            var plan = preview.terrainPlan(); var island = plan.descriptor();
            var components = IslandComponents.resolve(island, styles);
            String key = island.archetype() == IslandShapeArchetype.LAGOON ? "lagoon_" + components.shoreline()
                    : island.archetype() == IslandShapeArchetype.BROKEN_OCEAN_REEF ? "reef" : "land";
            if (found.containsKey(key) && treatments.contains(components.fringe())) continue;
            if (components.fringe() == IslandComponents.Fringe.NONE) continue;
            var bounds = plan.bounds();
            boolean selected = false;
            for (int x = bounds.minX(); x <= bounds.maxX() && !selected; x += 12)
                for (int z = bounds.minZ(); z <= bounds.maxZ() && !selected; z += 12) {
                    if (!interiors.skyweave$isFringe(island, x, z, settings)) continue;
                    var column = field.sampleChunkTerrainSnapshot(new net.minecraft.world.level.ChunkPos(x >> 4, z >> 4), settings).columnDataAt(x, z);
                    if (!island.equals(column.dominantDescriptor()) || !column.profile().exists()
                            || column.waterArchetype().exists()) continue;
                    int y = column.profile().topY();
                    if (interiors.skyweave$exteriorOf(island, x, z, settings).topY() != y) continue;
                    var pos = new BlockPos(x, y, z);
                    var provenance = SurfaceProvenance.fringe(island);
                    var row = new JsonObject(); row.addProperty("position", pos.toShortString());
                    row.addProperty("archetype", island.archetype().name()); row.addProperty("shoreline", components.shoreline().name());
                    row.addProperty("fringe", components.fringe().name()); row.addProperty("dryMetadata", true);
                    for (var stage : List.of(ChunkStatus.SURFACE, ChunkStatus.FEATURES)) {
                        var chunk = level.getChunk(x >> 4, z >> 4, stage);
                        var expected = provenance.material(x, z, 0, chunk.getBlockState(pos));
                        var actual = chunk.getBlockState(pos);
                        if (!actual.equals(expected) || !chunk.getFluidState(pos).isEmpty()
                                || !chunk.getFluidState(pos.above()).isEmpty())
                            throw new AssertionError(key + "/" + stage + ": " + actual + ", expected " + expected);
                        if (provenance.role() == SurfaceProvenance.Role.REEF
                                && !actual.is(net.minecraft.world.level.block.Blocks.STONE)
                                && !actual.is(net.minecraft.world.level.block.Blocks.CALCITE)
                                && !actual.is(net.minecraft.world.level.block.Blocks.SANDSTONE))
                            throw new AssertionError("Reef fringe acquired land soil: " + actual);
                        if (provenance.role() == SurfaceProvenance.Role.LAGOON_SHORE
                                && !actual.is(net.minecraft.world.level.block.Blocks.SAND))
                            throw new AssertionError("Lagoon fringe lost native sandy shore: " + actual);
                        row.addProperty(stage.toString(), actual.toString());
                    }
                    found.put(key, row); treatments.add(components.fringe()); selected = true;
                }
            if (found.keySet().containsAll(Set.of("land", "reef", "lagoon_NATIVE", "lagoon_COASTAL"))
                    && treatments.containsAll(Set.of(IslandComponents.Fringe.MIXED, IslandComponents.Fringe.DISINTEGRATING))) break;
        }
        var report = new JsonObject(); report.add("witnesses", new Gson().toJsonTree(found));
        boolean complete = found.keySet().containsAll(Set.of("land", "reef", "lagoon_NATIVE", "lagoon_COASTAL"))
                && treatments.containsAll(Set.of(IslandComponents.Fringe.MIXED, IslandComponents.Fringe.DISINTEGRATING));
        report.addProperty("complete", complete); report.addProperty("seed", level.getSeed());
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report));
        if (!complete) throw new AssertionError("Missing ocean-fringe controls: " + found.keySet() + "/" + treatments);
    }
    private OceanFringeProbe() {}
}
