package design.inherent.skyweavecompat.terrain;

import design.inherent.skyweavecompat.mixin.DeepDarkProjectorAccess;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.levelgen.RandomState;
import org.sathrek.sky_archipelago.config.SkyIslandSettings;
import org.sathrek.sky_archipelago.worldgen.generator.core.*;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;
import org.sathrek.sky_archipelago.worldgen.generator.field.model.ColumnData;

/** Shared cave ownership for stored biomes and physical-source consumers, without chunk loads. */
public final class CaveProjection {
    private CaveProjection() {}

    public static Holder<Biome> physical(SkyIslandChunkGenerator generator, RandomState random, BiomeSource source,
            Holder<Biome> original, int qx, int qy, int qz, Climate.Sampler sampler) {
        try (var ignored = SkyIslandDeepDarkBiomeProjector.suppressProjection()) {
            var settings = generator.resolvedSkyIslandSettings();
            var field = ((GeologicalGenerator)(Object)generator).skyweave$field(random);
            var exterior = exterior(field,settings,qx,qz);
            var result = fracturedBiome(field, source, exterior, original, qy);
            if (result.is(Biomes.DEEP_DARK) && result != original) return result;
            int y = qy * 4 + 2;
            if (DeepDarkProjectorAccess.skyweave$eligible(exterior.profile(),y)) {
                var deepDark = source.possibleBiomes().stream().filter(b -> b.is(Biomes.DEEP_DARK)).findFirst().orElse(null);
                if (deepDark != null) {
                    var proxy = RiverHostBiomeResolver.resolve(source,qx,-48 >> 2,qz,sampler,settings.rivers());
                    result = DeepDarkProjectorAccess.skyweave$project(original,proxy,deepDark,exterior.profile(),y);
                }
            }
            return relative(field,settings,exterior,source::getNoiseBiome,result,qx,qy,qz,sampler);
        }
    }

    public static Holder<Biome> relative(SkyIslandChunkGenerator generator, RandomState random, BiomeResolver resolver,
            Holder<Biome> original, int qx, int qy, int qz, Climate.Sampler sampler) {
        if (original.is(Biomes.DEEP_DARK)) return original;
        try (var ignored = SkyIslandDeepDarkBiomeProjector.suppressProjection()) {
            var settings = generator.resolvedSkyIslandSettings();
            var field = ((GeologicalGenerator)(Object)generator).skyweave$field(random);
            var exterior = exterior(field,settings,qx,qz);
            var ecological = fracturedBiome(field, generator.getBiomeSource(), exterior, original, qy);
            return ecological != original ? ecological : relative(field,settings,exterior,resolver,original,qx,qy,qz,sampler);
        }
    }

    private static Holder<Biome> fracturedBiome(IslandField field, BiomeSource source, ColumnData exterior,
            Holder<Biome> original, int qy) {
        var owner = exterior.dominantDescriptor();
        var styles = ((StyledTerrain)(Object)field).skyweave$styles();
        if (owner == null || styles == null || styles.style(owner).kind() != IslandStyles.Kind.DEEP_DARK) return original;
        int y = qy * 4 + 2;
        if (exterior.segments().stream().noneMatch(column -> y >= column.bottomY() - 4 && y <= column.topY() + 20)) return original;
        return source.possibleBiomes().stream().filter(biome -> biome.is(Biomes.DEEP_DARK)).findFirst().orElse(original);
    }

    private static ColumnData exterior(IslandField field, SkyIslandSettings settings, int qx, int qz) {
        return ((InteriorField)(Object)field).skyweave$planningColumn(qx*4+2,qz*4+2,settings,TerrainStage.EXTERIOR);
    }
    private static Holder<Biome> relative(IslandField field, SkyIslandSettings settings, ColumnData exterior,
            BiomeResolver resolver, Holder<Biome> original, int qx, int qy, int qz, Climate.Sampler sampler) {
        if (original.is(Biomes.DEEP_DARK)) return original;
        int x=qx*4+2,y=qy*4+2,z=qz*4+2;
        if (exterior.segments().stream().noneMatch(column -> y>=column.bottomY() && y<=column.topY()-16)) return original;
        var frame = ((GeologicalField)(Object)field).skyweave$frameAt(x,z,settings);
        if (frame == null || !frame.containsReference(frame.toReference(y))) return original;
        var proxy = resolver.getNoiseBiome(qx,frame.toReference(y)>>2,qz,sampler);
        return proxy.is(Biomes.LUSH_CAVES) || proxy.is(Biomes.DRIPSTONE_CAVES) ? proxy : original;
    }
}
