package design.inherent.skyweavecompat.terrain.structure;

import design.inherent.skyweavecompat.terrain.GeologicalGenerator;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.WorldGenLevel;
import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;

/** Physical postprocessing bound; no placement scope or neighboring chunk access is needed. */
public final class IslandSupportBounds {
    private IslandSupportBounds() {}

    public static int lowerBound(LevelReader world, int x, int startY, int z) {
        if (!(world instanceof WorldGenLevel generation)) return world.getMinBuildHeight();
        var level = generation.getLevel();
        if (!(level.getChunkSource().getGenerator() instanceof SkyIslandChunkGenerator generator)
                || !((GeologicalGenerator)(Object)generator).skyweave$isEnabled()) return world.getMinBuildHeight();
        var field = ((GeologicalGenerator)(Object)generator).skyweave$field(level.getChunkSource().randomState());
        var column = field.sampleColumn(x, z, generator.resolvedSkyIslandSettings());
        return Math.max(world.getMinBuildHeight(), column.exists() ? column.bottomY() - 1 : startY);
    }
}
