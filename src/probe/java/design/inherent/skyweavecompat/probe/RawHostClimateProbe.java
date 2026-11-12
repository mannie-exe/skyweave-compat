package design.inherent.skyweavecompat.probe;

import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.levelgen.DensityFunctions;
import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;

/** Actual generator-owned HostBiomeCache calls sharing one discriminating native source. */
final class RawHostClimateProbe {
    private RawHostClimateProbe() {}
    static JsonObject run(ServerLevel level,SkyIslandChunkGenerator original,Path output) throws Exception {
        var registry=level.registryAccess().registryOrThrow(Registries.BIOME);
        var low=registry.getHolderOrThrow(Biomes.PLAINS);var high=registry.getHolderOrThrow(Biomes.LUSH_CAVES);
        var source=MultiNoiseBiomeSource.createFromList(new Climate.ParameterList<>(List.of(
                Pair.of(Climate.parameters(0,0,0,0,0,0,0),low),Pair.of(Climate.parameters(0,0,0,0,.55f,0,0),high))));
        var near=new Climate.TargetPoint(0,0,0,0,0,0);var deep=new Climate.TargetPoint(0,0,0,0,5500,0);
        var zero=DensityFunctions.constant(0);
        var sampler=new Climate.Sampler(zero,zero,zero,zero,DensityFunctions.yClampedGradient(-48,48,0,.55),zero,List.of());
        var foreignSettings=level.registryAccess().registryOrThrow(Registries.NOISE_SETTINGS)
                .getHolderOrThrow(net.minecraft.world.level.levelgen.NoiseGeneratorSettings.OVERWORLD);
        var foreignA=new SkyIslandChunkGenerator(source,foreignSettings,original.embeddedSettings());
        var foreignB=new SkyIslandChunkGenerator(source,foreignSettings,original.embeddedSettings());
        source.getNoiseBiome(near);var baselineA=foreignA.sky_archipelago$resolveLandHostBiome(0,0,0,sampler);
        source.getNoiseBiome(deep);var baselineB=foreignB.sky_archipelago$resolveLandHostBiome(0,0,0,sampler);
        if(baselineA.equals(baselineB))throw new AssertionError("Foreign raw-host history control stopped discriminating");
        var ownedA=new SkyIslandChunkGenerator(source,original.generatorSettings(),original.embeddedSettings());
        var ownedB=new SkyIslandChunkGenerator(source,original.generatorSettings(),original.embeddedSettings());
        source.getNoiseBiome(near);var selectedA=ownedA.sky_archipelago$resolveLandHostBiome(0,0,0,sampler);
        source.getNoiseBiome(deep);var selectedB=ownedB.sky_archipelago$resolveLandHostBiome(0,0,0,sampler);
        var result=new JsonObject();result.addProperty("foreignAfterSurface",baselineA.getRegisteredName());result.addProperty("foreignAfterCave",baselineB.getRegisteredName());
        result.addProperty("ownedAfterSurface",selectedA.getRegisteredName());result.addProperty("ownedAfterCave",selectedB.getRegisteredName());
        result.addProperty("ownedHistoryIndependent",selectedA.equals(selectedB));result.addProperty("sharedSourceForeignHistoryPreserved",!baselineA.equals(baselineB));
        if(!selectedA.equals(selectedB)) {
            var report=new JsonObject();report.addProperty("complete",false);report.add("rawHostClimate",result);Files.writeString(output,report+"\n");
            throw new AssertionError("Owned raw host biome depends on native query history");
        }
        var surface=ownedA.sky_archipelago$resolveLandHostBiome(0,-12,0,sampler);
        var underground=ownedA.sky_archipelago$resolveLandHostBiome(0,12,0,sampler);
        if(!surface.equals(low) || !underground.equals(high))throw new AssertionError("Raw host sampler lost physical climate depth");
        result.addProperty("physicalDepthPreserved",true);
        var cacheType=Class.forName("org.sathrek.sky_archipelago.worldgen.generator.core.HostBiomeCache");
        var constructor=cacheType.getDeclaredConstructor(int.class,int.class);constructor.setAccessible(true);
        var cache=constructor.newInstance(2,2);
        var raw=cacheType.getDeclaredMethod("rawSample",BiomeSource.class,int.class,int.class,int.class,Climate.Sampler.class);raw.setAccessible(true);
        var entries=cacheType.getDeclaredField("rawSamples");entries.setAccessible(true);
        var samples=(com.google.common.cache.Cache<?,?>)entries.get(cache);
        try(var scope=design.inherent.skyweavecompat.climate.RawHostClimate.query(cache)) {
            source.getNoiseBiome(deep);var first=raw.invoke(cache,source,0,0,0,sampler);
            var witness=samples.asMap().keySet().iterator().next();
            for(int x=1;x<=8;x++)raw.invoke(cache,source,x,0,0,sampler);
            samples.cleanUp();
            if(samples.asMap().containsKey(witness) || samples.size()>2)throw new AssertionError("Native raw cache witness was not evicted");
            source.getNoiseBiome(near);
            if(!first.equals(raw.invoke(cache,source,0,0,0,sampler)))throw new AssertionError("Native raw cache eviction changed selection");
        }
        result.addProperty("boundedNativeRawCacheEviction",true);return result;
    }
}
