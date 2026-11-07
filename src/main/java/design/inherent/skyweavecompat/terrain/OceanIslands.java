package design.inherent.skyweavecompat.terrain;

import org.sathrek.sky_archipelago.worldgen.generator.field.IslandShapeArchetype;
import org.sathrek.sky_archipelago.worldgen.generator.field.WaterArchetypeColumnProfile;
import org.sathrek.sky_archipelago.worldgen.generator.field.model.HorizontalSample;
import org.sathrek.sky_archipelago.worldgen.generator.field.model.IslandDescriptor;

/** Adjusts native water columns without replacing their footprint, underbelly, or materials. */
public final class OceanIslands {
    public static boolean wetArchetype(IslandShapeArchetype type) {
        return type == IslandShapeArchetype.LAGOON;
    }
    public static WaterArchetypeColumnProfile adapt(IslandDescriptor island, HorizontalSample horizontal,
            WaterArchetypeColumnProfile nativeColumn, OceanProfile profile) {
        if (!nativeColumn.exists() || !wetArchetype(island.archetype())) return nativeColumn;
        var mode = profile.mode(island.seed());
        if (mode == OceanProfile.Mode.NATIVE || mode == OceanProfile.Mode.COASTAL && nativeColumn.water())
            return nativeColumn;
        if (mode == OceanProfile.Mode.COASTAL) {
            double angle = OceanProfile.unit(island.seed() ^ 0x179d5b396a1cb874L) * Math.PI * 2;
            double along = horizontal.localX() * Math.cos(angle) + horizontal.localZ() * Math.sin(angle);
            double across = horizontal.localZ() * Math.cos(angle) - horizontal.localX() * Math.sin(angle);
            if (along <= 0 || Math.abs(across) > profile.outletWidth() * 0.5) return nativeColumn;
        }
        int top = Math.min(nativeColumn.solidSurfaceY(), nativeColumn.fluidSurfaceY() - profile.outletDepth());
        int seal = Math.max(profile.sealThickness(), nativeColumn.solidSurfaceY() - nativeColumn.sealBottomY() + 1);
        int sealBottom = top - seal + 1;
        return new WaterArchetypeColumnProfile(WaterArchetypeColumnProfile.Region.WATER,
                WaterArchetypeColumnProfile.SurfaceRole.UNDERWATER_FLOOR,
                WaterArchetypeColumnProfile.BiomeProjection.SOURCE, top, nativeColumn.fluidSurfaceY(),
                sealBottom, Math.min(nativeColumn.underbellyBottomY(), sealBottom), nativeColumn.configuredOceanFluid());
    }
    private OceanIslands() {}
}
