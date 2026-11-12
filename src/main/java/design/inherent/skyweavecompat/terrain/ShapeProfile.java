package design.inherent.skyweavecompat.terrain;

import design.inherent.skyweavecompat.configuration.SettingSpec;
import design.inherent.skyweavecompat.configuration.RecordSettings;
import java.util.List;

/** Exterior choices around the protected native body and independent interiors. */
public record ShapeProfile(
        boolean enabled,
        @SettingSpec(minimum="0", maximum=".5", unit="factor") double strengthVariation,
        Outline outline,
        Fringe fringe,
        Fractures fractures,
        DeepDark deepDark) {
    public static final ShapeProfile INITIAL = new ShapeProfile(true, .15,
            new Outline(.65, 1.96, 26, 102), new Fringe(true, .3, .7, 64, .15, 1.6, 28, .6, .55, 1.2, 6),
            new Fractures(.35, List.of("minecraft:mushroom_fields", "minecraft:badlands",
                    "minecraft:eroded_badlands", "minecraft:wooded_badlands"), 72, 6, 32, .28, .10), DeepDark.DEFAULT);

    public ShapeProfile(boolean enabled, double strengthVariation, Outline outline, Fringe fringe, Fractures fractures) {
        this(enabled, strengthVariation, outline, fringe, fractures, DeepDark.DEFAULT);
    }

    public ShapeProfile {
        RecordSettings.validate(ShapeProfile.class, enabled, strengthVariation, outline, fringe, fractures, deepDark);
        if (outline == null || fringe == null || fractures == null || deepDark == null) throw new IllegalArgumentException("Missing shape group");
    }
    public record Outline(
            @SettingSpec(minimum="0", maximum="1", unit="probability", step=".01") double aspectChance,
            @SettingSpec(minimum="1", maximum="2.5", unit="factor", step=".05") double maximumAspectRatio,
            @SettingSpec(minimum="0", maximum="48", unit="blocks") int warpAmplitude,
            @SettingSpec(minimum="32", maximum="512", unit="blocks") int warpWavelength) {
        public Outline {
            RecordSettings.validate(Outline.class, aspectChance, maximumAspectRatio, warpAmplitude, warpWavelength);
        }
    }
    public record Fringe(
            boolean enabled,
            @SettingSpec(minimum="0", maximum="1", unit="probability", group="smoothRange", step=".01") double minimumSmoothFraction,
            @SettingSpec(minimum="0", maximum="1", unit="probability", group="smoothRange", step=".01") double maximumSmoothFraction,
            @SettingSpec(minimum="0", maximum="256", unit="blocks") int falloffDistance,
            @SettingSpec(minimum="0", maximum="1", unit="probability", step=".01") double disintegratingChance,
            @SettingSpec(minimum="1", maximum="3", unit="factor", step=".1") double disintegratingScale,
            @SettingSpec(minimum="8", maximum="128", unit="blocks") int fragmentScale,
            @SettingSpec(minimum="0", maximum=".8", unit="factor", step=".05") double fragmentDepthRatio,
            @SettingSpec(minimum="0", maximum="1", unit="probability", step=".01") double density,
            @SettingSpec(minimum=".5", maximum="4", unit="factor", step=".1") double falloffExponent,
            @SettingSpec(minimum="0", maximum="12", unit="blocks") int undersideAccent) {
        public Fringe {
            RecordSettings.validate(Fringe.class, enabled, minimumSmoothFraction, maximumSmoothFraction, falloffDistance, disintegratingChance, disintegratingScale, fragmentScale, fragmentDepthRatio, density, falloffExponent, undersideAccent);
            if (minimumSmoothFraction > maximumSmoothFraction) throw new IllegalArgumentException("Minimum smooth fraction exceeds maximum");
        }
        /** All possible widths, including the rare disintegrating case. */
        public int maximumReach(int radius) {
            return enabled ? (int)Math.ceil(Math.min(falloffDistance * disintegratingScale, radius * .8)) : 0;
        }
    }
    public record Fractures(
            @SettingSpec(minimum="0", maximum="1", unit="probability", step=".01") double chance,
            @SettingSpec(maximumEntries=128, unique=true, pattern="[a-z0-9_.-]+:[a-z0-9_./-]+") List<String> biomes,
            @SettingSpec(minimum="32", maximum="128", unit="blocks", group="cells") int cellSize,
            @SettingSpec(minimum="2", maximum="12", unit="blocks", group="cells") int gapWidth,
            @SettingSpec(minimum="0", maximum="48", unit="blocks") int verticalRange,
            @SettingSpec(minimum="0", maximum=".6", unit="probability", step=".01") double removalChance,
            @SettingSpec(minimum="0", maximum=".1", unit="probability", step=".01") double deepDarkChance) {
        public Fractures {
            RecordSettings.validate(Fractures.class, chance, biomes, cellSize, gapWidth, verticalRange, removalChance, deepDarkChance);
            if (gapWidth * 4 >= cellSize) throw new IllegalArgumentException("Fracture gap must be less than one quarter of cell size");
            biomes = List.copyOf(biomes);
        }
    }
    /** Natural cap and independent decoration draws; each device has at most one attempt group per chunk. */
    public record DeepDark(
            @SettingSpec(minimum="0", maximum="1", unit="probability", step=".01") double sculkCoverage,
            @SettingSpec(minimum="8", maximum="128", unit="blocks") int surfaceScale,
            @SettingSpec(minimum="0", maximum="1", unit="probability", step=".01") double veinChance,
            @SettingSpec(minimum="0", maximum="1", unit="probability", step=".01") double sensorChance,
            @SettingSpec(minimum="0", maximum="1", unit="probability", step=".01") double catalystChance,
            @SettingSpec(minimum="0", maximum="1", unit="probability", step=".01") double shriekerChance) {
        public static final DeepDark DEFAULT = new DeepDark(.94, 48, .25, .25, .08, .02);
        public DeepDark {
            RecordSettings.validate(DeepDark.class, sculkCoverage, surfaceScale, veinChance, sensorChance, catalystChance, shriekerChance);
        }
    }
    public double maximumAxisScale() { return enabled && outline.aspectChance() > 0 ? Math.sqrt(outline.maximumAspectRatio()) : 1; }
}
