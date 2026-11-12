package design.inherent.skyweavecompat.terrain.structure;

import design.inherent.skyweavecompat.configuration.SettingSpec;
import design.inherent.skyweavecompat.configuration.RecordSettings;

/** Work and support limits, independent of the encounter allowance. */
public record VillageFitSettings(
        @SettingSpec(minimum=".5", maximum="1", unit="fraction", step=".01") double minimumSupportFraction,
        @SettingSpec(minimum="0", maximum="8", unit="blocks") int maximumGroundGap,
        @SettingSpec(minimum="0", maximum="4", unit="blocks") int maximumUnsupportedDepth,
        boolean requireCompleteBuildingSupport,
        @SettingSpec(minimum=".25", maximum="1", unit="fraction", step=".01") double minimumRetainedBuildings,
        @SettingSpec(minimum="1", maximum="16", unit="attempts") int siteAttemptsPerVillage,
        @SettingSpec(minimum="1", maximum="32", unit="attempts") int assembliesPerSite,
        @SettingSpec(minimum="256", maximum="32768", unit="columns") int maximumColumnProbes) {
    public static final VillageFitSettings DEFAULT = new VillageFitSettings(.9, 3, 2, true, .65, 8, 12, 16384);
    public VillageFitSettings(double minimumSupportFraction,int maximumGroundGap,int maximumUnsupportedDepth,
            double minimumRetainedBuildings,int siteAttemptsPerVillage,int assembliesPerSite,int maximumColumnProbes) {
        this(minimumSupportFraction,maximumGroundGap,maximumUnsupportedDepth,true,minimumRetainedBuildings,
                siteAttemptsPerVillage,assembliesPerSite,maximumColumnProbes);
    }
    public VillageFitSettings {
        RecordSettings.validate(VillageFitSettings.class, minimumSupportFraction, maximumGroundGap, maximumUnsupportedDepth, requireCompleteBuildingSupport, minimumRetainedBuildings, siteAttemptsPerVillage, assembliesPerSite, maximumColumnProbes);
    }
}
