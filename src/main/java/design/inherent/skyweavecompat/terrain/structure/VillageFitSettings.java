package design.inherent.skyweavecompat.terrain.structure;

/** Work and support limits, independent of the encounter allowance. */
public record VillageFitSettings(double minimumSupportFraction, int maximumGroundGap,
        int maximumUnsupportedDepth, double minimumRetainedBuildings,
        int siteAttemptsPerVillage, int assembliesPerSite, int maximumColumnProbes) {
    public static final VillageFitSettings DEFAULT = new VillageFitSettings(.9, 3, 2, .65, 8, 12, 16384);
    public VillageFitSettings {
        if (!Double.isFinite(minimumSupportFraction) || minimumSupportFraction < .5 || minimumSupportFraction > 1
                || maximumGroundGap < 0 || maximumGroundGap > 8
                || maximumUnsupportedDepth < 0 || maximumUnsupportedDepth > 4
                || !Double.isFinite(minimumRetainedBuildings) || minimumRetainedBuildings < .25 || minimumRetainedBuildings > 1
                || siteAttemptsPerVillage < 1 || siteAttemptsPerVillage > 16
                || assembliesPerSite < 1 || assembliesPerSite > 32
                || maximumColumnProbes < 256 || maximumColumnProbes > 32768)
            throw new IllegalArgumentException("Invalid village support or fitting work limits");
    }
}
