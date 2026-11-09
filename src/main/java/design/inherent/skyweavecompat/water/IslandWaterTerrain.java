package design.inherent.skyweavecompat.water;

import design.inherent.skyweavecompat.terrain.*;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;

/** The upstream watershed reads actual island plans, never the underlying vanilla noise router. */
public final class IslandWaterTerrain {
    private IslandWaterTerrain() {}

    public static boolean applies(ChunkGenerator generator) {
        return generator instanceof GeologicalGenerator owned && owned.skyweave$isEnabled();
    }

    public static int height(ChunkGenerator generator, RandomState random, int x, int z) {
        var island = (SkyIslandChunkGenerator)generator;
        var field = ((GeologicalGenerator)(Object)island).skyweave$field(random);
        var column = ((InteriorField)(Object)field).skyweave$planningColumn(x, z,
                island.resolvedSkyIslandSettings(), TerrainStage.EXTERIOR);
        return column.segments().isEmpty() || column.dominantDescriptor() != null
                && column.dominantDescriptor().archetype().waterBased()
                || ((InteriorField)(Object)field).skyweave$isFringe(column.dominantDescriptor(), x, z,
                        island.resolvedSkyIslandSettings())
                ? generator.getMinY() : column.segments().getFirst().topY() + 1;
    }
}
