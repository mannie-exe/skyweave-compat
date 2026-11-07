package design.inherent.skyweavecompat.terrain;

import java.util.List;

/** Horizontal exterior choices; the native vertical body and independent interiors retain authority. */
public record ShapeProfile(boolean enabled, double strengthVariation, Outline outline, Edges edges, Fractures fractures) {
    public static final ShapeProfile INITIAL = new ShapeProfile(true, .15,
            new Outline(.65, 1.96, 26, 102), new Edges(.45, .625, 57, 24, 26),
            new Fractures(.35, List.of("minecraft:mushroom_fields", "minecraft:badlands",
                    "minecraft:eroded_badlands", "minecraft:wooded_badlands"), 72, 6, 32, .28, .01, .25));

    public ShapeProfile {
        if (!Double.isFinite(strengthVariation) || strengthVariation < 0 || strengthVariation > .5)
            throw new IllegalArgumentException("Shape strength variation must be between 0 and 0.5");
        if (outline == null || edges == null || fractures == null) throw new IllegalArgumentException("Missing shape group");
    }
    public record Outline(double aspectChance, double maximumAspectRatio, int warpAmplitude, int warpWavelength) {
        public Outline {
            if (!chance(aspectChance) || !Double.isFinite(maximumAspectRatio) || maximumAspectRatio < 1 || maximumAspectRatio > 2.5
                    || warpAmplitude < 0 || warpAmplitude > 48 || warpWavelength < 32 || warpWavelength > 512)
                throw new IllegalArgumentException("Invalid outline; aspect ratio 1–2.5, warp 0–48 blocks, wavelength 32–512");
        }
    }
    public record Edges(double chance, double affectedFraction, int bandWidth, int amplitude, int wavelength) {
        public Edges {
            if (!ShapeProfile.chance(chance) || !ShapeProfile.chance(affectedFraction) || bandWidth < 8 || bandWidth > 96
                    || amplitude < 0 || amplitude > 48 || amplitude > bandWidth || wavelength < 8 || wavelength > 128)
                throw new IllegalArgumentException("Invalid perimeter; amplitude must fit the 8–96 block edge band");
        }
    }
    public record Fractures(double chance, List<String> biomes, int cellSize, int gapWidth,
            int verticalRange, double removalChance, double deepDarkChance, double sculkPatchChance) {
        public Fractures {
            if (!ShapeProfile.chance(chance) || !ShapeProfile.chance(removalChance) || removalChance > .6
                    || !ShapeProfile.chance(deepDarkChance) || deepDarkChance > .1 || !ShapeProfile.chance(sculkPatchChance)
                    || cellSize < 32 || cellSize > 128 || gapWidth < 2 || gapWidth > 12 || gapWidth * 4 >= cellSize
                    || verticalRange < 0 || verticalRange > 48 || biomes == null || biomes.size() > 128)
                throw new IllegalArgumentException("Invalid fractured exterior: cell/gap bounds or probability out of range");
            biomes = List.copyOf(biomes);
            if (biomes.stream().distinct().count() != biomes.size()
                    || biomes.stream().anyMatch(id -> !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")))
                throw new IllegalArgumentException("Fractured biomes must be unique namespaced biome IDs");
        }
    }
    private static boolean chance(double value) { return Double.isFinite(value) && value >= 0 && value <= 1; }
    public double maximumAxisScale() { return enabled && outline.aspectChance() > 0 ? Math.sqrt(outline.maximumAspectRatio()) : 1; }
}
