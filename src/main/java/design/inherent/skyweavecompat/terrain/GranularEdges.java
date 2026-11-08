package design.inherent.skyweavecompat.terrain;

/** Seeded outer density lobes. Distances are outside the retained body, never removed from it. */
public final class GranularEdges {
    public record Fragment(int topOffset, int depth) {}
    public record Boundary(double angle, double distance, double outwardX, double outwardZ) {}
    private GranularEdges() {}

    public static boolean disintegrating(long seed, ShapeProfile.Edges settings) {
        return settings.enabled() && IslandStyles.unit(seed, 0x653a27L) < settings.disintegratingChance();
    }
    public static double width(long seed, double radius, ShapeProfile.Edges settings) {
        return settings.enabled() ? Math.min(radius * .8, settings.falloffDistance()
                * (disintegrating(seed, settings) ? settings.disintegratingScale() : 1)) : 0;
    }
    public static double granularFraction(long seed, ShapeProfile.Edges settings) {
        double mixed = 1 - settings.minimumSmoothFraction()
                - IslandStyles.unit(seed, 0x47a38eL) * (settings.maximumSmoothFraction() - settings.minimumSmoothFraction());
        return disintegrating(seed, settings) ? mixed + (1 - mixed) * .45 : mixed;
    }

    /** A conservative maximum is kept separate from the continuously varying local reach. */
    public static double localWidth(long seed, double radius, double angle, ShapeProfile.Edges settings) {
        double ux = Math.cos(angle), uz = Math.sin(angle);
        double broad = IslandStyles.noise(seed ^ 0x2b5c31L, ux * 2.3, uz * 2.3);
        double detail = IslandStyles.noise(seed ^ 0x682fa9L, ux * 5.7, uz * 5.7);
        return width(seed, radius, settings) * (.2 + .8 * IslandStyles.smooth(.5 + broad * .65 + detail * .25));
    }

    public static Fragment sample(long seed, double x, double z, Boundary boundary,
            double radius, int nominalHeight, ShapeProfile.Edges settings) {
        double outsideDistance = boundary.distance();
        double width = localWidth(seed, radius, boundary.angle(), settings);
        if (width <= 0 || settings.density() == 0 || settings.fragmentDepthRatio() == 0
                || outsideDistance < 0 || outsideDistance >= width) return null;
        double mask = IslandStyles.perimeterMask(seed, boundary.angle(), granularFraction(seed, settings));
        // An irregular empty separation admits detached shoulders without shaving the native rim.
        double separation = 3 + (IslandStyles.noise(seed ^ 0x3b92L, x / 45, z / 45) + 1) * 3;
        if (outsideDistance < separation || mask <= .02) return null;
        double progress = outsideDistance / width;
        double falloff = Math.pow(1 - progress, settings.falloffExponent());
        double scale = settings.fragmentScale();
        double px = x + IslandStyles.noise(seed ^ 0x1a7dL, x / scale, z / scale) * scale * .2;
        double pz = z + IslandStyles.noise(seed ^ 0x934fL, x / scale, z / scale) * scale * .2;
        int gx = (int)Math.floor(px / scale), gz = (int)Math.floor(pz / scale);
        double best = 0, height = .5;
        for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
            int cx = gx + dx, cz = gz + dz;
            long key = seed ^ cx * 0x9e3779b97f4a7c15L ^ cz * 0x632be59bd9b4e019L;
            double ox = (cx + .5 + (IslandStyles.unit(key, 1) - .5) * .65) * scale;
            double oz = (cz + .5 + (IslandStyles.unit(key, 2) - .5) * .65) * scale;
            // Estimate distance at the stable cell center, rather than changing the
            // grid spacing per sampled column. Thinning increases mean separation
            // outward without moving centers or introducing concentric tiers.
            double cellProgress = Math.clamp((outsideDistance + (ox - px) * boundary.outwardX()
                    + (oz - pz) * boundary.outwardZ()) / width, 0, 1);
            if (IslandStyles.unit(key, 6) > .95 - cellProgress * .65) continue;
            double size = (.28 + Math.pow(IslandStyles.unit(key, 3), 1.7) * .5) * (1 - cellProgress * .5);
            double rx = scale * size * (.8 + IslandStyles.unit(key, 4) * .4);
            double rz = scale * size * (.8 + IslandStyles.unit(key, 7) * .4);
            double lobe = 1 - Math.pow((px - ox) / rx, 2) - Math.pow((pz - oz) / rz, 2);
            lobe += IslandStyles.noise(key ^ 0x741cL, px / (scale * .3), pz / (scale * .3)) * .3;
            lobe -= (1 - settings.density()) * .5 + (1 - falloff * mask) * .65;
            if (lobe > best) { best = lobe; height = IslandStyles.unit(key, 5); }
        }
        if (best <= .02) return null;
        int depth = (int)Math.round(nominalHeight * settings.fragmentDepthRatio()
                * (.8 + height * .4) * falloff * Math.pow(Math.min(1, best * 1.7), .45));
        if (depth < 8) return null;
        int top = (int)Math.round(5 - progress * 32 + (height - .5) * 12
                + IslandStyles.noise(seed ^ 0x39aaL, px / 18, pz / 18) * 4);
        return new Fragment(top, depth);
    }
}
