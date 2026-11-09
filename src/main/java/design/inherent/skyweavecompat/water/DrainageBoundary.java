package design.inherent.skyweavecompat.water;

import design.inherent.skyweavecompat.terrain.GeologicalGenerator;
import design.inherent.skyweavecompat.terrain.InteriorField;
import design.inherent.skyweavecompat.terrain.TerrainStage;
import net.minecraft.world.level.levelgen.RandomState;
import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;

/** Physical exterior occupancy, independent of ocean biome names and completed drainage. */
public record DrainageBoundary(Kind kind, int level, long owner) {
    public enum Kind { LAND, LOCAL_WATER, VOID }
    public static DrainageBoundary sample(SkyIslandChunkGenerator generator, RandomState random, int x, int z) {
        var field = (InteriorField)(Object)((GeologicalGenerator)(Object)generator).skyweave$field(random);
        var settings = generator.resolvedSkyIslandSettings();
        var column = field.skyweave$planningColumn(x, z, settings, TerrainStage.EXTERIOR);
        var host = column.dominantDescriptor();
        if (column.segments().isEmpty() || host == null || field.skyweave$isFringe(host, x, z, settings))
            return new DrainageBoundary(Kind.VOID, generator.getMinY(), 0);
        var water = column.waterArchetype();
        if (water.water()) return new DrainageBoundary(Kind.LOCAL_WATER, water.fluidSurfaceY(), host.seed());
        return new DrainageBoundary(Kind.LAND, column.segments().getFirst().topY(), host.seed());
    }
}
