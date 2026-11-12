package design.inherent.skyweavecompat.mixin;

import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = {"org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandWaterArchetypeBiomeProjector$Context",
        "org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandRiverBiomeProjector$Context"}, remap = false)
public interface ProjectionClimateContext {
    @Accessor("generator") SkyIslandChunkGenerator skyweave$climateGenerator();
    @Accessor("randomState") net.minecraft.world.level.levelgen.RandomState skyweave$climateRandom();
}
