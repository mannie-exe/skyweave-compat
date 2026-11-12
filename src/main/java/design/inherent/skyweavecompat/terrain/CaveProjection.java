package design.inherent.skyweavecompat.terrain;

import design.inherent.skyweavecompat.climate.UndergroundDepth;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.levelgen.RandomState;
import org.sathrek.sky_archipelago.worldgen.generator.core.*;

/** Native climate lookup with height-qualified ownership and a separate natural depth input. */
public final class CaveProjection {
    private CaveProjection() {}

    public static Holder<Biome> physical(SkyIslandChunkGenerator generator, RandomState random, BiomeSource source,
            Holder<Biome> original, int qx, int qy, int qz, Climate.Sampler sampler) {
        return resolve(generator, random, source, original, qx, qy, qz, sampler);
    }

    public static Holder<Biome> relative(SkyIslandChunkGenerator generator, RandomState random, BiomeResolver resolver,
            Holder<Biome> original, int qx, int qy, int qz, Climate.Sampler sampler) {
        return resolve(generator, random, generator.projectedBiomeSource().climateSource(), original, qx, qy, qz, sampler);
    }

    private static Holder<Biome> resolve(SkyIslandChunkGenerator generator, RandomState random, BiomeSource source,
            Holder<Biome> original, int qx, int qy, int qz, Climate.Sampler sampler) {
        // Only native multi-noise admits replacing one climate coordinate. Foreign 3-D
        // selectors keep their own behavior instead of being approximated through Y.
        if (source==null || source.getClass()!=MultiNoiseBiomeSource.class) return original;
        var multiNoise=(MultiNoiseBiomeSource)source;
        try (var ignored = SkyIslandDeepDarkBiomeProjector.suppressProjection()) {
            int x = qx * 4 + 2, y = qy * 4 + 2, z = qz * 4 + 2;
            var field = ((GeologicalGenerator)(Object)generator).skyweave$field(random);
            var context = ((InteriorField)(Object)field).skyweave$contextAt(x, z, generator.resolvedSkyIslandSettings());
            var body = context.biomeOwner(y);
            // A special-island air halo may extend into genuinely unowned sky, never
            // through a different physical contributor or an unknown occupied region.
            var nearby = body!=null?body:context.contributions(y).isEmpty()?context.owner(y,20):null;
            var styles = ((StyledTerrain)(Object)field).skyweave$styles();
            if (nearby != null && styles != null && styles.style(nearby.island()).kind() == IslandStyles.Kind.DEEP_DARK)
                return source.possibleBiomes().stream().filter(biome -> biome.is(Biomes.DEEP_DARK)).findFirst().orElse(original);

            // Discard upstream fixed-height/bottom-band projection for this owned-world route.
            var policy = design.inherent.skyweavecompat.climate.ClimateConfiguration.profile();
            if (body == null || y > body.exterior().topY() - policy.minimumCaveCover())
                return surfaceFallback(generator,multiNoise,qx,qy,qz,sampler);
            var point = sampler.sample(qx, qy, qz);
            long depth = Climate.quantizeCoord((float)UndergroundDepth.sample(y,
                    body.exterior().bottomY(), body.exterior().topY(), policy.bottomDepth()));
            var selected = select(generator,multiNoise,new Climate.TargetPoint(point.temperature(), point.humidity(),
                    point.continentalness(), point.erosion(), depth, point.weirdness()));
            return selected.is(Biomes.LUSH_CAVES) || selected.is(Biomes.DRIPSTONE_CAVES) || selected.is(Biomes.DEEP_DARK)
                    ? selected : surfaceFallback(generator,multiNoise,qx,qy,qz,sampler);
        }
    }

    /** One native-index boundary shared by owned depth queries. */
    public static Holder<Biome> select(SkyIslandChunkGenerator generator,MultiNoiseBiomeSource source,Climate.TargetPoint point) {
        return ((design.inherent.skyweavecompat.climate.StableClimateSource)(Object)source).skyweave$stableBiome(point);
    }

    private static Holder<Biome> surfaceFallback(SkyIslandChunkGenerator generator,MultiNoiseBiomeSource source,
            int qx,int qy,int qz,Climate.Sampler sampler) {
        // Outside/body-surface selection replaces depth only; even Y-sensitive foreign
        // parameter data retains the other five physical quart coordinates.
        var point=sampler.sample(qx,qy,qz);
        return select(generator,source,new Climate.TargetPoint(point.temperature(),point.humidity(),
                point.continentalness(),point.erosion(),0,point.weirdness()));
    }
}
