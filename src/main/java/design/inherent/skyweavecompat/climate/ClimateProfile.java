package design.inherent.skyweavecompat.climate;

/** Separate climate altitude and native underground-depth tuning; never physical coordinates. */
public record ClimateProfile(double altitudeScale, int airReach, int minimumCaveCover, double bottomDepth) {
    public static final ClimateProfile DEFAULT = new ClimateProfile(1, 256, 16, 1.1);
    public ClimateProfile {
        if (!Double.isFinite(altitudeScale) || altitudeScale < 0 || altitudeScale > 4
                || airReach < 0 || airReach > 512 || minimumCaveCover < 4 || minimumCaveCover > 128
                || !Double.isFinite(bottomDepth) || bottomDepth <= 0 || bottomDepth > 2)
            throw new IllegalArgumentException("Invalid climate altitude, air reach, cave cover or depth");
    }
}
