package design.inherent.skyweavecompat.climate;

import design.inherent.skyweavecompat.configuration.SettingSpec;
import design.inherent.skyweavecompat.configuration.RecordSettings;

/** Separate climate altitude and native underground-depth tuning; never physical coordinates. */
public record ClimateProfile(
        @SettingSpec(minimum="0", maximum="4", step="0.1", unit="factor", group="altitude") double altitudeScale,
        @SettingSpec(minimum="0", maximum="512", unit="blocks", group="altitude") int airReach,
        @SettingSpec(minimum="4", maximum="128", unit="blocks", group="underground") int minimumCaveCover,
        @SettingSpec(minimum="0", maximum="2", exclusiveMinimum=true, step="0.1", unit="depth", group="underground") double bottomDepth) {
    public static final ClimateProfile DEFAULT = new ClimateProfile(1, 256, 16, 1.1);
    public ClimateProfile {
        RecordSettings.validate(ClimateProfile.class, altitudeScale, airReach, minimumCaveCover, bottomDepth);
    }
}
