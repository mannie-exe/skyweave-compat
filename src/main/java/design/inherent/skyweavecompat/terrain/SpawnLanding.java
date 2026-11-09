package design.inherent.skyweavecompat.terrain;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;
import java.util.*;

/** Startup-only validation after native fallback selection, never called by terrain generation. */
public final class SpawnLanding {
    private SpawnLanding() {}

    public static BlockPos resolve(ServerLevel level, BlockPos proposed) {
        var requested = new HashSet<Long>();
        BlockPos found;
        // Native fallback can select a fully wet perimeter host or a fractured opening.
        // Inspect nearby accepted metadata before paying for additional generated chunks.
        if (level.getChunkSource().getGenerator() instanceof SkyIslandChunkGenerator generator) {
            var field = ((GeologicalGenerator)(Object)generator).skyweave$field(level.getChunkSource().randomState());
            var settings = generator.resolvedSkyIslandSettings();
            var original = field.sampleDominantIslandDescriptor(proposed.getX(), proposed.getZ(), settings);
            var clusters = (ClusterField)(Object)field;
            var preferred = original == null ? null : clusters.skyweave$owner(original, settings);
            var previews = field.collectIslandPreviewsInRadius(proposed.getX(), proposed.getZ(), 8192, settings).stream()
                    .filter(preview -> preview.family() == org.sathrek.sky_archipelago.worldgen.generator.field.model.IslandFamily.SATELLITE
                            && !preview.archetype().waterBased() && preview.terrainPlan() != null)
                    .sorted(Comparator.<org.sathrek.sky_archipelago.worldgen.generator.field.model.IslandPreview>comparingInt(preview ->
                            preferred != null && preferred.equals(clusters.skyweave$owner(preview.terrainPlan().descriptor(), settings)) ? 0 : 1)
                            .thenComparingDouble(preview -> Math.hypot(preview.x() - (double)proposed.getX(),
                                    preview.z() - (double)proposed.getZ()))).limit(24).toList();
            int inspected = 0;
            for (var preview : previews) {
                int step = Math.max(4, preview.radius() / 3);
                for (int dx : new int[]{0, -step, step}) for (int dz : new int[]{0, -step, step}) {
                    if (++inspected > 216 || requested.size() >= 32) break;
                    int x = preview.x() + dx, z = preview.z() + dz;
                    var column = field.sampleColumnProfile(x, z, settings);
                    if (!column.exists()) continue;
                    int top = column.topY();
                    var ocean = field.sampleWaterArchetypeColumnProfile(x, z, settings);
                    var river = field.sampleHydrologyColumn(x, z, settings);
                    if (ocean.water() && ocean.fluidSurfaceY() > top || river.wet() && river.fluidSurfaceY() > top) continue;
                    var companion = preview.terrainPlan().descriptor();
                    found = near(level, new BlockPos(x, top + 1, z), requested, pos -> {
                        var owner = ((InteriorField)(Object)field).skyweave$planningColumn(
                                pos.getX(), pos.getZ(), settings, TerrainStage.EXTERIOR).dominantDescriptor();
                        return companion.equals(owner);
                    });
                    if (found != null) return found;
                }
            }
        }
        throw new IllegalStateException("No supported dry companion-island landing within the bounded fresh-spawn search at " + proposed);
    }

    private static BlockPos near(ServerLevel level, BlockPos proposed, Set<Long> requested,
            java.util.function.Predicate<BlockPos> belongs) {
        // Generated surfaces can differ from predicted columns after carving and features.
        for (int radius = 0; radius <= 16; radius += 4) {
            for (int dx = -radius; dx <= radius; dx += 4) for (int dz = -radius; dz <= radius; dz += 4) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                int x = proposed.getX() + dx, z = proposed.getZ() + dz;
                if (!belongs.test(new BlockPos(x, proposed.getY(), z))) continue;
                long key = net.minecraft.world.level.ChunkPos.asLong(x >> 4, z >> 4);
                if (!requested.contains(key) && requested.size() >= 32) continue;
                requested.add(key);
                var chunk = level.getChunk(x >> 4, z >> 4);
                var feet = new BlockPos(x, chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15) + 1, z);
                if (feet.getY() <= level.getMinBuildHeight() || feet.getY() + 1 >= level.getMaxBuildHeight()) continue;
                if (!level.getWorldBorder().isWithinBounds(feet)) continue;
                if (level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP)
                        && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                        && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
                        && level.getFluidState(feet).isEmpty() && level.getFluidState(feet.above()).isEmpty()) return feet;
            }
        }
        return null;
    }
}
