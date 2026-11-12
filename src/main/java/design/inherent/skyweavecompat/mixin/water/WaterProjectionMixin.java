package design.inherent.skyweavecompat.mixin.water;

import design.inherent.skyweavecompat.Feature;
import design.inherent.skyweavecompat.OptionalIntegrations;
import design.inherent.skyweavecompat.climate.IslandClimate;
import design.inherent.skyweavecompat.terrain.GeologicalGenerator;
import design.inherent.skyweavecompat.water.IslandWaterTerrain;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.RandomState;
import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Stored and queried river biomes follow the accepted water column, not raw continental river bands. */
@Mixin(value = SkyIslandChunkGenerator.class, remap = false)
public abstract class WaterProjectionMixin {
    @Shadow @Final private Map<Climate.Sampler, RandomState> projectionRandomStates;
    @Shadow abstract net.minecraft.world.level.biome.BiomeSource rawBiomeSource();

    @Inject(method = "sky_archipelago$projectedBiome", at = @At("RETURN"), cancellable = true)
    private void river(int qx, int qy, int qz, Climate.Sampler sampler, CallbackInfoReturnable<Holder<Biome>> result) {
        var generator = (SkyIslandChunkGenerator)(Object)this;
        if (!IslandWaterTerrain.applies(generator)) return;
        var random = projectionRandomStates.get(sampler);
        if (random == null) return;
        int x = QuartPos.toBlock(qx) + 2, z = QuartPos.toBlock(qz) + 2, y = QuartPos.toBlock(qy) + 2;
        var field = ((GeologicalGenerator)(Object)generator).skyweave$field(random);
        var river = field.sampleHydrologyColumn(x, z, generator.resolvedSkyIslandSettings());
        if (!river.wet() || y < river.solidSurfaceY() - 4 || y > river.fluidSurfaceY() + 4) return;
        var host = rawBiomeSource().getNoiseBiome(qx, QuartPos.fromBlock(river.originalSurfaceY()), qz, sampler);
        var pos = new BlockPos(x, river.fluidSurfaceY(), z);
        boolean cold = host.value().coldEnoughToSnow(OptionalIntegrations.enabled(Feature.CLIMATE)
                ? design.inherent.skyweavecompat.climate.ClimateRuntime.temperaturePosition(generator,random,pos) : pos);
        var selected = cold ? Biomes.FROZEN_RIVER : Biomes.RIVER;
        rawBiomeSource().possibleBiomes().stream().filter(biome -> biome.is(selected)).findFirst().ifPresent(result::setReturnValue);
    }
}
