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
        if (mode == OceanProfile.Mode.NATIVE || mode == OceanProfile.Mode.COASTAL && !nativeColumn.shore())
            return nativeColumn;
        double opening = 1;
        if (mode == OceanProfile.Mode.COASTAL) {
            opening = coveOpening(island, horizontal.localX(), horizontal.localZ(), profile);
            if (opening == 0) return nativeColumn;
        }
        int lowered = Math.min(nativeColumn.solidSurfaceY(), nativeColumn.fluidSurfaceY() - profile.outletDepth());
        int top = (int)Math.round(nativeColumn.solidSurfaceY() + opening * (lowered - nativeColumn.solidSurfaceY()));
        if (top == nativeColumn.solidSurfaceY()) return nativeColumn;
        int seal = Math.max(profile.sealThickness(), nativeColumn.solidSurfaceY() - nativeColumn.sealBottomY() + 1);
        int sealBottom = top - seal + 1;
        boolean submerged = top < nativeColumn.fluidSurfaceY();
        return new WaterArchetypeColumnProfile(submerged ? WaterArchetypeColumnProfile.Region.WATER : nativeColumn.region(),
                submerged ? WaterArchetypeColumnProfile.SurfaceRole.UNDERWATER_FLOOR : nativeColumn.surfaceRole(),
                submerged ? WaterArchetypeColumnProfile.BiomeProjection.SOURCE : nativeColumn.biomeProjection(), top, nativeColumn.fluidSurfaceY(),
                sealBottom, Math.min(nativeColumn.underbellyBottomY(), sealBottom), nativeColumn.configuredOceanFluid());
    }
    /** Angular mouth in the same elliptical local frame as the native basin, with rounded shoulders. */
    public static double coveOpening(IslandDescriptor island, double x, double z, OceanProfile profile) {
        double direction = OceanProfile.unit(island.seed() ^ 0x179d5b396a1cb874L) * Math.PI * 2;
        double angle = Math.atan2(z / island.radiusZ(), x / island.radiusX());
        double distance = Math.abs(Math.atan2(Math.sin(angle - direction), Math.cos(angle - direction)));
        return IslandStyles.smooth((Math.toRadians(profile.coveOpeningDegrees() * .5) - distance)
                / Math.toRadians(profile.coveShoulderDegrees()));
    }
    private OceanIslands() {}
}
