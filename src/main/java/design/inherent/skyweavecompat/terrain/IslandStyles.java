package design.inherent.skyweavecompat.terrain;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.sathrek.sky_archipelago.worldgen.generator.field.model.*;

/** Seeded horizontal modifiers and ecology metadata, memoized by one native field. */
public final class IslandStyles {
    public enum Kind { NATIVE, FRACTURED, DEEP_DARK }
    public record Style(Kind kind, boolean disturbed, String hostBiome) {}
    private final ShapeProfile profile;
    private final BiomeTerrainSampler biomes;
    private final Cache<IslandDescriptor, Style> styles = CacheBuilder.newBuilder().maximumSize(8192).build();
    public IslandStyles(ShapeProfile profile, BiomeTerrainSampler biomes) { this.profile = profile; this.biomes = biomes; }
    public ShapeProfile profile() { return profile; }
    public Style style(IslandDescriptor island) {
        try { return styles.get(island, () -> {
            var biome = biomes.sample(island.centerX(), island.centerY() + island.plateauHeight(), island.centerZ()).biomeId();
            String id = biome == null ? "" : biome.toString();
            Kind kind = Kind.NATIVE;
            boolean anchor = island.family() == IslandFamily.ANCHOR_PLATEAU && !island.archetype().waterBased();
            if (profile.enabled() && anchor) {
                var f = profile.fractures();
                var deep = unit(island.seed(), 0x18c93b71L) < f.deepDarkChance()
                        ? biomes.sample(island.centerX(), -48, island.centerZ()).biomeId() : null;
                if (deep != null && deep.toString().equals("minecraft:deep_dark")
                        && design.inherent.skyweavecompat.OptionalIntegrations.enabled(design.inherent.skyweavecompat.Feature.CAVE_BIOMES))
                    kind = Kind.DEEP_DARK;
                else if (f.biomes().contains(id) && unit(island.seed(), 0x39a280b3L) < f.chance()) kind = Kind.FRACTURED;
            }
            return new Style(kind, profile.enabled() && !island.archetype().waterBased()
                    && island.family() != IslandFamily.SPIRE && profile.edges().enabled()
                    && profile.edges().falloffDistance() > 0 && profile.edges().fragmentDepthRatio() > 0
                    && profile.edges().density() > 0, id);
        }); } catch (java.util.concurrent.ExecutionException error) { throw new IllegalStateException("Cannot resolve island style", error.getCause()); }
    }
    public static double aspect(long seed, ShapeProfile profile) {
        if (!profile.enabled() || unit(seed, 0x39bf1a19L) >= profile.outline().aspectChance()) return 1;
        return 1 + unit(seed, 0x714dc9e3L) * (profile.outline().maximumAspectRatio() - 1);
    }
    /** Independent per-island draws around the configured base; zero retains fixed strength. */
    public static double strengthScale(long seed, long salt, ShapeProfile profile) {
        return profile.enabled() ? 1 + (unit(seed, salt) * 2 - 1) * profile.strengthVariation() : 1;
    }
    public static double unit(long seed, long salt) { return OceanProfile.unit(seed ^ salt); }
    public static double smooth(double value) { double t = Math.clamp(value, 0, 1); return t * t * (3 - 2 * t); }
    public static double noise(long seed, double x, double z) {
        long ix = (long)Math.floor(x), iz = (long)Math.floor(z);
        double tx = smooth(x - ix), tz = smooth(z - iz);
        double a = lattice(seed, ix, iz), b = lattice(seed, ix + 1, iz);
        double c = lattice(seed, ix, iz + 1), d = lattice(seed, ix + 1, iz + 1);
        return ((a + (b - a) * tx) * (1 - tz) + (c + (d - c) * tx) * tz) * 2 - 1;
    }
    private static double lattice(long seed, long x, long z) {
        return OceanProfile.unit(seed ^ x * 0x9e3779b97f4a7c15L ^ z * 0x632be59bd9b4e019L);
    }
    public static double perimeterMask(long seed, double angle, double fraction) {
        if (fraction <= 0) return 0;
        if (fraction >= 1) return 1;
        int arcs = 3 + (int)(unit(seed, 0x94b231b1L) * 3);
        double phase = unit(seed, 0x78b1d311L) * Math.PI * 2;
        double bend = noise(seed ^ 0x56bb21L, Math.cos(angle) * 2, Math.sin(angle) * 2) * .7;
        return smooth((Math.cos(angle * arcs + phase + bend) - Math.cos(Math.PI * fraction)) / .3 + .5);
    }
}
