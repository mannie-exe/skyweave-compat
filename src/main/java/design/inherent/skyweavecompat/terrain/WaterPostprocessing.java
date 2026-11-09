package design.inherent.skyweavecompat.terrain;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.sathrek.sky_archipelago.config.SkyIslandSettings;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;

/** Schedules only actual edge fluids of deliberately opened native water islands. */
public final class WaterPostprocessing {
    private WaterPostprocessing() {}
    public static void mark(ChunkAccess chunk, IslandField field, SkyIslandSettings settings) {
        if (((ProfiledTerrain)(Object)field).skyweave$profile() == null || !OceanConfiguration.profile().enabled()) return;
        var pos = new BlockPos.MutableBlockPos();
        for (int x = chunk.getPos().getMinBlockX(); x <= chunk.getPos().getMaxBlockX(); x++)
            for (int z = chunk.getPos().getMinBlockZ(); z <= chunk.getPos().getMaxBlockZ(); z++) {
                var island = field.sampleDominantIslandDescriptor(x, z, settings);
                if (island == null || !OceanIslands.supportsShorelineTreatment(island.archetype())
                        || OceanConfiguration.profile().mode(island.seed()) == OceanProfile.Mode.NATIVE) continue;
                var water = field.sampleWaterArchetypeColumnProfile(x, z, settings);
                if (!water.water()) continue;
                for (int y = Math.max(chunk.getMinBuildHeight(), water.solidSurfaceY() + 1);
                        y <= Math.min(chunk.getMaxBuildHeight() - 1, water.fluidSurfaceY()); y++) {
                    if (chunk.getBlockState(pos.set(x, y, z)).getFluidState().isEmpty()) continue;
                    for (var direction : Direction.values()) {
                        int nx = x + direction.getStepX(), ny = y + direction.getStepY(), nz = z + direction.getStepZ();
                        if (!field.sampleWaterArchetypeColumnProfile(nx, nz, settings).containsWaterAt(ny)
                                && field.sampleSolidSegments(nx, nz, settings).stream().noneMatch(s -> s.contains(ny))) {
                            chunk.markPosForPostprocessing(pos);
                            break;
                        }
                    }
                }
            }
    }
}
