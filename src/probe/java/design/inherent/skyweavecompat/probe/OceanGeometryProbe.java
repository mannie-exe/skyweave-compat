package design.inherent.skyweavecompat.probe;

import com.google.gson.*;
import design.inherent.skyweavecompat.terrain.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.sathrek.sky_archipelago.config.SkyIslandSettings;
import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandShapeArchetype;
import org.sathrek.sky_archipelago.worldgen.generator.field.internal.IslandShapeSampler;
import java.util.HashSet;

/** Native metadata/query/fill witnesses for normal-sized wet hosts and their dry outer fragments. */
final class OceanGeometryProbe {
    static JsonArray inspect(ServerLevel level, SkyIslandChunkGenerator generator, IslandField field,
            IslandShapeSampler shapes, SkyIslandSettings settings, int radius) {
        var rows = new JsonArray();
        var selected = new HashSet<IslandShapeArchetype>();
        var profile = ((StyledTerrain)(Object)shapes).skyweave$styles().profile();
        if (!profile.enabled() || !profile.fringe().enabled() || profile.fringe().falloffDistance() == 0
                || profile.fringe().density() == 0 || profile.fringe().fragmentDepthRatio() == 0) return rows;
        for (var preview : field.collectIslandPreviewsInRadius(0, 0, radius, settings)) {
            var plan = preview.terrainPlan();
            if (plan == null || !preview.archetype().waterBased() || selected.contains(preview.archetype())) continue;
            var island = plan.descriptor();
            var row = new JsonObject();
            row.addProperty("archetype", island.archetype().name());
            row.add("descriptor", new Gson().toJsonTree(island));
            double equivalent = Math.sqrt((double)island.radiusX() * island.radiusZ());
            var geometry = GenerationConfiguration.profile();
            if (equivalent < geometry.anchorMinRadius() - 1 || equivalent > geometry.anchorMaxRadius() + 1)
                throw new AssertionError("Ocean anchor escaped normal size authority: " + island);
            long began = System.nanoTime(); int queries = 0;
            int reach = (int)Math.ceil(island.maxRadius() * 1.9 + 16);
            BlockPos witness = null;
            BlockPos bodyWitness = null;
            search: for (int dx = -reach; dx <= reach; dx += 8) for (int dz = -reach; dz <= reach; dz += 8) {
                int x = island.centerX() + dx, z = island.centerZ() + dz;
                var horizontal = shapes.sampleHorizontal(plan, x, z); queries++;
                var fragment = ((FringeColumn)(Object)horizontal).skyweave$fragment();
                var nativeColumn = shapes.sampleWaterArchetypeColumnProfile(island, horizontal);
                if (bodyWitness == null && nativeColumn.exists()) {
                    var completed = field.sampleWaterArchetypeColumnProfile(x, z, settings);
                    if (completed.exists()) {
                        bodyWitness = new BlockPos(x, completed.solidSurfaceY(), z);
                        row.addProperty("bodyRegion", completed.region().name());
                        row.addProperty("bodySurface", completed.surfaceRole().name());
                    }
                }
                if (fragment == null) continue;
                if (nativeColumn.exists())
                    throw new AssertionError("Dry fringe replaced a native wet column");
                int y = island.centerY() + island.plateauHeight() + fragment.topOffset();
                witness = new BlockPos(x, y, z); break search;
            }
            row.addProperty("metadataQueries", queries);
            row.addProperty("metadataSeconds", (System.nanoTime() - began) / 1e9);
            if (witness == null) throw new AssertionError("No native ocean fringe in bounded fixture: " + island.seed());
            var random = level.getChunkSource().randomState();
            var base = generator.getBaseColumn(witness.getX(), witness.getZ(), level, random).getBlock(witness.getY());
            if (base.isAir() || !base.getFluidState().isEmpty()) throw new AssertionError("Ocean fringe base-column mismatch: " + witness);
            level.getChunk(witness.getX() >> 4, witness.getZ() >> 4);
            var actual = level.getBlockState(witness);
            if (actual.isAir() || !actual.getFluidState().isEmpty()) throw new AssertionError("Ocean fringe FULL-chunk mismatch: " + witness);
            row.addProperty("fragment", witness.toShortString());
            row.addProperty("baseColumn", base.toString()); row.addProperty("fullChunk", actual.toString());
            // Scan order can encounter an exterior fragment before the core. Use the native
            // sampler separately to find an existing body, including dry Broken Reef columns.
            if (bodyWitness == null) {
                search: for (int dx = -island.radiusX(); dx <= island.radiusX(); dx += 16)
                    for (int dz = -island.radiusZ(); dz <= island.radiusZ(); dz += 16) {
                        int x = island.centerX() + dx, z = island.centerZ() + dz;
                        var column = shapes.sampleWaterArchetypeColumnProfile(island, shapes.sampleHorizontal(plan, x, z));
                        if (!column.exists()) continue;
                        column = field.sampleWaterArchetypeColumnProfile(x, z, settings);
                        if (!column.exists()) continue; // Native completion may remove a raw shoreline sliver.
                        bodyWitness = new BlockPos(x, column.solidSurfaceY(), z);
                        row.addProperty("bodyRegion", column.region().name());
                        row.addProperty("bodySurface", column.surfaceRole().name()); break search;
                    }
            }
            if (bodyWitness == null) throw new AssertionError("Native ocean body missing");
            level.getChunk(bodyWitness.getX() >> 4, bodyWitness.getZ() >> 4);
            if (level.getBlockState(bodyWitness).isAir()) throw new AssertionError("Native ocean body lost in FULL chunk at "
                    + bodyWitness + "; completed water=" + field.sampleWaterArchetypeColumnProfile(bodyWitness.getX(), bodyWitness.getZ(), settings)
                    + "; completed solids=" + field.sampleSolidSegments(bodyWitness.getX(), bodyWitness.getZ(), settings)
                    + "; base=" + generator.getBaseColumn(bodyWitness.getX(), bodyWitness.getZ(), level, random).getBlock(bodyWitness.getY()));
            row.addProperty("body", bodyWitness.toShortString());
            row.addProperty("bodyBlock", level.getBlockState(bodyWitness).toString());
            row.addProperty("queryAndFillPassed", true);
            rows.add(row); selected.add(preview.archetype());
        }
        if (rows.isEmpty()) throw new AssertionError("No eligible ocean geometry in bounded fixture");
        return rows;
    }
    private OceanGeometryProbe() {}
}
