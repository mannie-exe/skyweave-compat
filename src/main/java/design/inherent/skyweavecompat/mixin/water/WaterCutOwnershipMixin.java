package design.inherent.skyweavecompat.mixin.water;

import com.AdamNew.dynamicwaters.HydrologyManager;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import design.inherent.skyweavecompat.terrain.GeologicalGenerator;
import design.inherent.skyweavecompat.terrain.InteriorField;
import design.inherent.skyweavecompat.terrain.TerrainStage;
import design.inherent.skyweavecompat.water.IslandWaterColumns;
import design.inherent.skyweavecompat.water.IslandWaterTerrain;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;

/** Snapshot fill and upstream post-carver repair share the same real pre-water terrain boundary. */
@Mixin(value = HydrologyManager.class, remap = false)
public abstract class WaterCutOwnershipMixin {
    @WrapMethod(method = "calculateRiverCarving")
    private static HydrologyManager.RiverCarving owned(double x, double z, int originalY, int sea,
            ChunkGenerator generator, LevelHeightAccessor level, RandomState random,
            Operation<HydrologyManager.RiverCarving> original) {
        if (!IslandWaterTerrain.applies(generator) || random == null || TerrainStage.current() == TerrainStage.EXTERIOR)
            return original.call(x, z, originalY, sea, generator, level, random);
        var sky = (SkyIslandChunkGenerator)generator;
        var field = ((GeologicalGenerator)(Object)sky).skyweave$field(random);
        var column = ((InteriorField)(Object)field).skyweave$planningColumn(Mth.floor(x), Mth.floor(z),
                sky.resolvedSkyIslandSettings(), TerrainStage.INTERIOR);
        int maximumY = generator.getMinY() + generator.getGenDepth();
        if (column.segments().isEmpty() || column.dominantDescriptor() == null
                || column.dominantDescriptor().archetype().waterBased()
                || ((InteriorField)(Object)field).skyweave$isFringe(column.dominantDescriptor(), Mth.floor(x), Mth.floor(z),
                        sky.resolvedSkyIslandSettings()))
            return IslandWaterColumns.unchanged(originalY, maximumY);
        var top = column.segments().getFirst();
        // Post-carver repair otherwise mistakes an already excavated bed for the
        // original surface, and can build a lowland foundation through empty sky.
        var cut = original.call(x, z, top.topY(), sea, generator, level, random);
        return IslandWaterColumns.fitToSupport(cut, top, maximumY)
                ? cut : IslandWaterColumns.unchanged(originalY, maximumY);
    }
}
