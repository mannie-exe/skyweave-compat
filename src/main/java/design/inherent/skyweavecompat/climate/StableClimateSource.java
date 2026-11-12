package design.inherent.skyweavecompat.climate;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.*;

/** Explicit owned-selection boundary; ordinary native source queries keep their original index. */
public interface StableClimateSource {
    Holder<Biome> skyweave$stableBiome(Climate.TargetPoint point);
}
