package design.inherent.skyweavecompat.terrain;

/** Versioned terrain parameters. A changed layout requires a new generation identity. */
public record GenerationProfile(int spacing, int jitter, int centerMinY, int centerMaxY,
        int anchorMinRadius, int anchorMaxRadius, int anchorPlateau, int anchorMinHeight, int anchorMaxHeight,
        int companionMinRadius, int companionMaxRadius, int companionMinOrbit, int companionMaxOrbit,
        int companionMinOffsetY, int companionMaxOffsetY, int minimumClusterGap,
        int floorClearance, int buildingHeadroom, double activation) {
    public static final String ID = "skyweave_compat:islands_v1";
    public static final int MIN_Y = -128;
    public static final int MAX_Y = 768;
    public static final GenerationProfile INITIAL = new GenerationProfile(
            2400, 750, 200, 490, 270, 370, 28, 250, 400, 40, 110, 470, 680,
            -60, 50, 350, 32, 200, 0.85);

    public GenerationProfile {
        if (spacing <= 0 || jitter < 0 || 2L * jitter >= spacing || centerMinY > centerMaxY
                || centerMinY < MIN_Y || centerMaxY >= MAX_Y
                || anchorMinRadius <= 0 || anchorMinRadius > anchorMaxRadius
                || anchorPlateau <= 0 || anchorMinHeight <= anchorPlateau + 64 || anchorMaxHeight < anchorMinHeight
                || companionMinRadius <= 0
                || companionMinRadius > companionMaxRadius || companionMinOrbit < 0
                || companionMinOrbit > companionMaxOrbit || companionMinOffsetY > companionMaxOffsetY
                || minimumClusterGap < 0 || floorClearance < 0 || buildingHeadroom < 0
                || floorClearance > MAX_Y - MIN_Y || buildingHeadroom > MAX_Y - MIN_Y
                || companionMinOffsetY < MIN_Y - MAX_Y || companionMaxOffsetY > MAX_Y - MIN_Y
                || !Double.isFinite(activation) || activation <= 0 || activation > 1
                || spacing > 16384 || anchorMaxRadius > 500 || companionMaxRadius > 500
                || companionMaxOrbit > 8192 || anchorMaxHeight > 600 || anchorPlateau > 128) {
            throw new IllegalArgumentException("Invalid or unreachable island profile");
        }
        int radius = clusterReach(anchorMaxRadius, companionMaxOrbit, companionMaxRadius);
        if (Math.ceilDiv(2L * jitter + 2L * radius + minimumClusterGap, spacing) > 8) {
            throw new IllegalArgumentException("Profile requires more than eight neighboring cells for clearance");
        }
        if (safeMinimumY(centerMinY, cliffDepth(anchorMaxHeight, anchorPlateau), anchorPlateau, companionMaxOffsetY, floorClearance)
                > safeMaximumY(centerMaxY, anchorPlateau, companionMinOffsetY, buildingHeadroom)) {
            throw new IllegalArgumentException("No island placement fits the floor clearance and building headroom");
        }
    }

    // Bounds enclose native 1.4 plans; include one block for rounded companion offsets.
    private static int clusterReach(int anchorRadius, int orbit, int companionRadius) {
        double scale = ShapeConfiguration.profile().maximumAxisScale();
        return Math.max(supportRadius((int)Math.ceil(anchorRadius * scale)),
                orbit + supportRadius((int)Math.ceil(Math.max(companionRadius, 28) * scale)) + 1);
    }
    public static int supportRadius(int radius) {
        // Rotated, warped native land plans and broken-reef margins bound both axes.
        double land = Math.sqrt(2) * (1.34 * 1.65 + 0.14) * radius + 2;
        double reef = radius + 1.5 * Math.max(8, radius / 2) + 10;
        var shapes = ShapeConfiguration.profile();
        int apron = shapes.enabled() ? shapes.edges().maximumReach(radius) : 0;
        return (int)Math.ceil(Math.max(land, reef)) + apron;
    }
    // Native full-coverage nominal depth: 1.18 * (plateau + .97 * cliff) + .2 * capped hang.
    // This selects a shape parameter, not a replacement density/taper algorithm.
    private static int cliffDepth(int targetHeight, int plateau) {
        return Math.max(1, (int)Math.round(((targetHeight - 3.2) / 1.18 - plateau) / 0.97));
    }
    public int cliffDepth(int targetHeight) { return cliffDepth(targetHeight, anchorPlateau); }
    public int safeMinimumY(int targetHeight) {
        return safeMinimumY(centerMinY, cliffDepth(targetHeight), anchorPlateau, companionMaxOffsetY, floorClearance);
    }
    public int clusterReach() { return clusterReach(anchorMaxRadius, companionMaxOrbit, companionMaxRadius); }
    /** A farther cell's centers cannot approach within two cluster reaches plus the gap. */
    public int conflictCells() { return (int)Math.ceilDiv(2L * jitter + 2L * clusterReach() + minimumClusterGap, spacing); }
    /** Point/area lookup, including the distance from a cell edge to its nominal center. */
    public int lookupCells() { return lookupCells(clusterReach()); }
    public int lookupCells(int reach) { return (int)Math.ceilDiv((long)jitter + reach + (spacing + 1L) / 2, spacing); }
    public int safeMinimumY() { return safeMinimumY(anchorMaxHeight); }
    public int safeMaximumY() { return safeMaximumY(centerMaxY, anchorPlateau, companionMinOffsetY, buildingHeadroom); }

    /** Native 1.4 bound, relative to center; does not truncate the sampled body. */
    public static int depthBelowCenter(int cliff, int plateau, int hang) {
        // For land, expand shoulder depth by 1.18; bounded relief, chips, gullies,
        // integer rounding and the capped hanger contribute the conservative remainder.
        int land = (int)Math.ceil(1.18 * 0.97 * cliff + 0.18 * plateau + 64);
        // Native wet profiles clamp underbellies to their total descriptor budget;
        // the extra 48 includes basin floors below the center/plateau datum.
        int wet = cliff + hang + Math.max(48, Math.ceilDiv(2 * plateau, 3) + 16);
        return Math.max(land, wet);
    }

    private static int safeMinimumY(int requested, int cliff, int plateau, int companionOffset, int clearance) {
        int depth = Math.max(depthBelowCenter(cliff, plateau, 48), depthBelowCenter(95, 14, 28) - companionOffset);
        return Math.max(requested, MIN_Y + clearance + depth);
    }
    private static int safeMaximumY(int requested, int plateau, int companionOffset, int headroom) {
        int rise = Math.max(plateau + 48, companionOffset + 14 + 48);
        return Math.min(requested, MAX_Y - 1 - headroom - rise);
    }
}
