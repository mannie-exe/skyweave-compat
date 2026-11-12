package design.inherent.skyweavecompat.terrain;

import java.util.List;

/** Exterior choices around the protected native body and independent interiors. */
public record ShapeProfile(boolean enabled, double strengthVariation, Outline outline, Fringe fringe, Fractures fractures,
        DeepDark deepDark) {
    public static final ShapeProfile INITIAL = new ShapeProfile(true, .15,
            new Outline(.65, 1.96, 26, 102), new Fringe(true, .3, .7, 64, .15, 1.6, 28, .6, .55, 1.2, 6),
            new Fractures(.35, List.of("minecraft:mushroom_fields", "minecraft:badlands",
                    "minecraft:eroded_badlands", "minecraft:wooded_badlands"), 72, 6, 32, .28, .10), DeepDark.DEFAULT);

    public ShapeProfile(boolean enabled, double strengthVariation, Outline outline, Fringe fringe, Fractures fractures) {
        this(enabled, strengthVariation, outline, fringe, fractures, DeepDark.DEFAULT);
    }

    public ShapeProfile {
        if (!Double.isFinite(strengthVariation) || strengthVariation < 0 || strengthVariation > .5)
            throw new IllegalArgumentException("Shape strength variation must be between 0 and 0.5");
        if (outline == null || fringe == null || fractures == null || deepDark == null) throw new IllegalArgumentException("Missing shape group");
    }
    public record Outline(double aspectChance, double maximumAspectRatio, int warpAmplitude, int warpWavelength) {
        public Outline {
            if (!chance(aspectChance) || !Double.isFinite(maximumAspectRatio) || maximumAspectRatio < 1 || maximumAspectRatio > 2.5
                    || warpAmplitude < 0 || warpAmplitude > 48 || warpWavelength < 32 || warpWavelength > 512)
                throw new IllegalArgumentException("Invalid outline; aspect ratio 1–2.5, warp 0–48 blocks, wavelength 32–512");
        }
    }
    public record Fringe(boolean enabled, double minimumSmoothFraction, double maximumSmoothFraction,
            int falloffDistance, double disintegratingChance, double disintegratingScale, int fragmentScale,
            double fragmentDepthRatio, double density, double falloffExponent, int undersideAccent) {
        public Fringe {
            if (!chance(minimumSmoothFraction) || !chance(maximumSmoothFraction)
                    || minimumSmoothFraction > maximumSmoothFraction || !chance(disintegratingChance)
                    || falloffDistance < 0 || falloffDistance > 256 || fragmentScale < 8 || fragmentScale > 128
                    || !Double.isFinite(disintegratingScale) || disintegratingScale < 1 || disintegratingScale > 3
                    || !Double.isFinite(fragmentDepthRatio) || fragmentDepthRatio < 0 || fragmentDepthRatio > .8
                    || !chance(density) || !Double.isFinite(falloffExponent) || falloffExponent < .5 || falloffExponent > 4
                    || undersideAccent < 0 || undersideAccent > 12)
                throw new IllegalArgumentException("Invalid granular perimeter bounds, density, or depth");
        }
        /** All possible widths, including the rare disintegrating case. */
        public int maximumReach(int radius) {
            return enabled ? (int)Math.ceil(Math.min(falloffDistance * disintegratingScale, radius * .8)) : 0;
        }
    }
    public record Fractures(double chance, List<String> biomes, int cellSize, int gapWidth,
            int verticalRange, double removalChance, double deepDarkChance) {
        public Fractures {
            if (!ShapeProfile.chance(chance) || !ShapeProfile.chance(removalChance) || removalChance > .6
                    || !ShapeProfile.chance(deepDarkChance) || deepDarkChance > .1
                    || cellSize < 32 || cellSize > 128 || gapWidth < 2 || gapWidth > 12 || gapWidth * 4 >= cellSize
                    || verticalRange < 0 || verticalRange > 48 || biomes == null || biomes.size() > 128)
                throw new IllegalArgumentException("Invalid fractured exterior: cell/gap bounds or probability out of range");
            biomes = List.copyOf(biomes);
            if (biomes.stream().distinct().count() != biomes.size()
                    || biomes.stream().anyMatch(id -> !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")))
                throw new IllegalArgumentException("Fractured biomes must be unique namespaced biome IDs");
        }
    }
    /** Natural cap and independent decoration draws; each device has at most one attempt group per chunk. */
    public record DeepDark(double sculkCoverage, int surfaceScale, double veinChance, double sensorChance,
            double catalystChance, double shriekerChance) {
        public static final DeepDark DEFAULT = new DeepDark(.94, 48, .25, .25, .08, .02);
        public DeepDark {
            if (!chance(sculkCoverage) || surfaceScale < 8 || surfaceScale > 128 || !chance(veinChance)
                    || !chance(sensorChance) || !chance(catalystChance) || !chance(shriekerChance))
                throw new IllegalArgumentException("Invalid Deep Dark coverage, surface scale (8–128), or decoration chance");
        }
    }
    private static boolean chance(double value) { return Double.isFinite(value) && value >= 0 && value <= 1; }
    public double maximumAxisScale() { return enabled && outline.aspectChance() > 0 ? Math.sqrt(outline.maximumAspectRatio()) : 1; }
}
