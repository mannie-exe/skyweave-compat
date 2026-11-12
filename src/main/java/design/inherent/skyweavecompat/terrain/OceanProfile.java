package design.inherent.skyweavecompat.terrain;

import design.inherent.skyweavecompat.configuration.SettingSpec;
import design.inherent.skyweavecompat.configuration.RecordSettings;

/** Optional edge treatments of native wet islands; no independent basin generator. */
public record OceanProfile(
        boolean enabled,
        @SettingSpec(minimum="0", maximum="1", unit="probability", group="admission", step=".01") double admissionChance,
        @SettingSpec(minimum="0", maximum="10", unit="weight", group="variants", step=".1") double lagoonWeight,
        @SettingSpec(minimum="0", maximum="10", unit="weight", group="variants", step=".1") double reefRingWeight,
        @SettingSpec(minimum="0", maximum="10", unit="weight", group="variants", step=".1") double deadOceanShelfWeight,
        @SettingSpec(minimum="0", maximum="10", unit="weight", group="variants", step=".1") double brokenReefWeight,
        @SettingSpec(minimum="0", maximum="1", unit="probability", group="shoreline", step=".01") double coastalChance,
        @SettingSpec(minimum="0", maximum="1", unit="probability", group="shoreline", step=".01") double perimeterChance,
        @SettingSpec(minimum="30", maximum="180", unit="degrees", group="cove") int coveOpeningDegrees,
        @SettingSpec(minimum="1", maximum="89", unit="degrees", group="cove") int coveShoulderDegrees,
        @SettingSpec(minimum="1", maximum="8", unit="blocks") int outletDepth,
        @SettingSpec(minimum="3", maximum="16", unit="blocks") int sealThickness,
        boolean spireCompanions,
        @SettingSpec(minimum="0", maximum="64", unit="blocks") int spireClearance) {
    public enum Mode { NATIVE, COASTAL, PERIMETER }
    public static final OceanProfile INITIAL = new OceanProfile(true, 0.20, 1, 0, 0, 1, 0.25, 0, 100, 20, 2, 4, true, 16);
    public OceanProfile {
        RecordSettings.validate(OceanProfile.class, enabled, admissionChance, lagoonWeight, reefRingWeight, deadOceanShelfWeight, brokenReefWeight, coastalChance, perimeterChance, coveOpeningDegrees, coveShoulderDegrees, outletDepth, sealThickness, spireCompanions, spireClearance);
        if (coastalChance + perimeterChance > 1 || coveShoulderDegrees * 2 >= coveOpeningDegrees)
            throw new IllegalArgumentException("Ocean edge probabilities or cove shoulder/opening are inconsistent");
    }
    public boolean admits(long seed) {
        return enabled && unit(seed ^ 0x607b1c192a8e34d5L) < admissionChance;
    }
    public Mode mode(long seed) {
        if (!enabled) return Mode.NATIVE;
        double roll = unit(seed ^ 0x42af48b6121c5e93L);
        return roll < perimeterChance ? Mode.PERIMETER
                : roll < perimeterChance + coastalChance ? Mode.COASTAL : Mode.NATIVE;
    }
    public static double unit(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return ((value ^ (value >>> 31)) >>> 11) * 0x1.0p-53;
    }
}
