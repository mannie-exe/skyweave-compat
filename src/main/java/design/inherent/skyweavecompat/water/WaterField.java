package design.inherent.skyweavecompat.water;

import net.minecraft.world.level.levelgen.RandomState;
import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;

/** Attached only when the optional watershed adaptation is selected. */
public interface WaterField {
    void skyweave$water(SkyIslandChunkGenerator generator, RandomState random);
}
