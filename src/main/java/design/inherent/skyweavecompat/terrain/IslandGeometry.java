package design.inherent.skyweavecompat.terrain;

import org.sathrek.sky_archipelago.worldgen.generator.field.model.ClusterDescriptor;
import org.sathrek.sky_archipelago.worldgen.generator.field.model.IslandDescriptor;
import org.sathrek.sky_archipelago.worldgen.generator.field.model.IslandFamily;
import org.sathrek.sky_archipelago.worldgen.generator.field.internal.IslandNoise;

/** Retains upstream archetypes and shape noise while separating body and companion dimensions. */
public final class IslandGeometry {
    private IslandGeometry() {}

    public static ClusterDescriptor cluster(ClusterDescriptor source, GenerationProfile profile) {
        var noise = new IslandNoise(source.seed());
        int x = source.cellX() * profile.spacing() + profile.spacing() / 2
                + noise.sampleInt(source.seed(), 163, -profile.jitter(), profile.jitter());
        int z = source.cellZ() * profile.spacing() + profile.spacing() / 2
                + noise.sampleInt(source.seed(), 197, -profile.jitter(), profile.jitter());
        int y = noise.sampleInt(source.seed(), 223, profile.safeMinimumY(targetHeight(source.seed(), profile)), profile.safeMaximumY());
        return new ClusterDescriptor(source.cellX(), source.cellZ(), x, y, z,
                source.heightBand(), source.sizeBand(), source.tier(), source.baseRotation(),
                source.archetype(), source.satelliteCount(), source.spireCount(), source.seed());
    }

    public static IslandDescriptor island(IslandDescriptor source, ClusterDescriptor cluster,
            GenerationProfile profile) {
        var noise = new IslandNoise(source.seed());
        boolean anchor = source.family() == IslandFamily.ANCHOR_PLATEAU;
        boolean spire = source.family() == IslandFamily.SPIRE;
        var archetype = spire && source.archetype().waterBased()
                ? org.sathrek.sky_archipelago.worldgen.generator.field.IslandShapeArchetype.CLASSIC : source.archetype();
        int minRadius = anchor ? profile.anchorMinRadius() : spire ? 12 : profile.companionMinRadius();
        int maxRadius = anchor ? profile.anchorMaxRadius() : spire ? 28 : profile.companionMaxRadius();
        var oceans = OceanConfiguration.profile();
        if (anchor && OceanIslands.wetArchetype(source.archetype())
                && oceans.mode(source.seed()) == OceanProfile.Mode.PERIMETER) {
            minRadius = oceans.perimeterMinRadius();
            maxRadius = oceans.perimeterMaxRadius();
        }
        int rx = noise.sampleInt(source.seed(), 11, minRadius, maxRadius);
        int rz = noise.sampleInt(source.seed(), 13, minRadius, maxRadius);
        if (!archetype.waterBased()) {
            double aspect = IslandStyles.aspect(source.seed(), ShapeConfiguration.profile());
            if (aspect > 1) {
                double equivalent = Math.sqrt((double)rx * rz);
                rx = Math.max(1, (int)Math.round(equivalent * Math.sqrt(aspect)));
                rz = Math.max(1, (int)Math.round(equivalent / Math.sqrt(aspect)));
            }
        }
        int plateau = anchor ? profile.anchorPlateau() : spire ? 6 : 14;
        int cliff = anchor ? profile.cliffDepth(targetHeight(cluster.seed(), profile)) : spire ? 95 : noise.sampleInt(source.seed(), 31, 40, 90);
        int x = cluster.centerX(), z = cluster.centerZ(), y = cluster.centerY();
        if (!anchor) {
            double angle = Math.atan2(source.centerZ() - cluster.centerZ(), source.centerX() - cluster.centerX());
            double orbit = noise.sampleInt(source.seed(), 7, profile.companionMinOrbit(), profile.companionMaxOrbit());
            x += (int)Math.round(Math.cos(angle) * orbit);
            z += (int)Math.round(Math.sin(angle) * orbit);
            // Choose a valid altitude before constructing geometry; never clip an island afterward.
            int lower = Math.max(y + profile.companionMinOffsetY(), GenerationProfile.MIN_Y + profile.floorClearance()
                    + GenerationProfile.depthBelowCenter(cliff, plateau, 28));
            int upper = Math.min(y + profile.companionMaxOffsetY(), GenerationProfile.MAX_Y - 1 - profile.buildingHeadroom() - 14 - 48);
            y = noise.sampleInt(source.seed(), 17, lower, upper);
        }
        var result = new IslandDescriptor(source.family(), x, y, z, rx, rz, Math.max(rx, rz),
                source.rotation(), archetype, plateau, cliff, anchor ? 48 : 28,
                scale(source.hangOffsetX(), rx, source.radiusX()), scale(source.hangOffsetZ(), rz, source.radiusZ()),
                scale(source.tailRadiusX(), rx, source.radiusX()), scale(source.tailRadiusZ(), rz, source.radiusZ()),
                source.erosionStrength(), source.seed());
        if (Math.hypot(x - cluster.centerX(), z - cluster.centerZ())
                + GenerationProfile.supportRadius(result.maxRadius()) > profile.clusterReach()) {
            throw new IllegalStateException("Island descriptor exceeds the validated cluster envelope");
        }
        return result;
    }

    public static int targetHeight(long clusterSeed, GenerationProfile profile) {
        return new IslandNoise(clusterSeed).sampleInt(clusterSeed, 241, profile.anchorMinHeight(), profile.anchorMaxHeight());
    }

    public static GeologicalFrame geologicalFrame(IslandDescriptor anchor) {
        return GeologicalFrame.nativeBody(anchor.centerY(), anchor.plateauHeight(), anchor.cliffDepth(),
                anchor.hangDepth(), anchor.archetype().waterBased());
    }

    private static int scale(int value, int target, int original) {
        return (int)Math.round((double)value * target / Math.max(1, original));
    }
}
