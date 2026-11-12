package design.inherent.skyweavecompat.terrain;

import design.inherent.skyweavecompat.configuration.SettingSpec;
import design.inherent.skyweavecompat.configuration.RecordSettings;

/** Interior choices do not change descriptor geometry or the anchor's geological datum. */
public record InteriorProfile(
        @SettingSpec(minimum="0", maximum="1", unit="probability", group="interiorChoice", step=".01") double noiseChance,
        @SettingSpec(minimum="0", maximum="1", unit="probability", group="interiorChoice", step=".01") double hollowChance,
        @SettingSpec(minimum="4", maximum="48", unit="blocks") int shellThickness,
        @SettingSpec(minimum="16", maximum="256", unit="blocks", group="noiseScale") double noiseHorizontalScale,
        @SettingSpec(minimum="8", maximum="128", unit="blocks", group="noiseScale") double noiseVerticalScale,
        @SettingSpec(minimum=".01", maximum=".3", unit="factor", step=".01") double passageWidth,
        @SettingSpec(minimum=".2", maximum="1", unit="factor", step=".01") double chamberThreshold,
        @SettingSpec(minimum=".2", maximum=".8", unit="factor", step=".01") double hollowRadius,
        @SettingSpec(minimum=".08", maximum=".35", unit="factor", step=".01") double hollowHeight,
        @SettingSpec(minimum="0", maximum="1", unit="probability", step=".01") double openingChance,
        boolean nativeCarvers,
        Skylights skylights) {
    public static final InteriorProfile INITIAL = new InteriorProfile(
            0.55, 0.18, 12, 64, 32, 0.075, 0.48, 0.58, 0.24, 0.35, true, Skylights.INITIAL);
    public record Skylights(
            boolean enabled,
            boolean noiseInteriors,
            @SettingSpec(minimum="2", maximum="12", unit="blocks", step=".5") double radius,
            @SettingSpec(minimum="0", maximum=".5", unit="factor", step=".01") double warp,
            @SettingSpec(minimum="0", maximum=".5", unit="factor", step=".01") double aspectVariation) {
        public static final Skylights INITIAL = new Skylights(true, false, 6, 0.25, 0.25);
        public Skylights {
            RecordSettings.validate(Skylights.class, enabled, noiseInteriors, radius, warp, aspectVariation);
        }
    }
    public InteriorProfile {
        RecordSettings.validate(InteriorProfile.class, noiseChance, hollowChance, shellThickness, noiseHorizontalScale, noiseVerticalScale, passageWidth, chamberThreshold, hollowRadius, hollowHeight, openingChance, nativeCarvers, skylights);
        if (noiseChance + hollowChance > 1 || skylights == null) throw new IllegalArgumentException("Interior probabilities exceed one or skylights are missing");
    }
}
