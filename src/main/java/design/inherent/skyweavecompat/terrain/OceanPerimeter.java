package design.inherent.skyweavecompat.terrain;

/** A bounded outer silhouette for additive dry fragments, not a replacement water footprint. */
public final class OceanPerimeter {
    static final int RAYS = 96;
    static final int SAMPLES_PER_RAY = 128;
    @FunctionalInterface public interface Footprint { boolean contains(int x, int z); }
    private final double[] radii;

    private OceanPerimeter(double[] radii) { this.radii = radii; }

    /** Sample outside inward so holes between native reef cells remain inside the silhouette. */
    public static OceanPerimeter sample(int centerX, int centerZ, int maximumRadius, Footprint footprint) {
        double far = maximumRadius * 1.9 + 16;
        double step = far / SAMPLES_PER_RAY;
        double[] raw = new double[RAYS];
        for (int ray = 0; ray < RAYS; ray++) {
            double angle = ray * Math.PI * 2 / RAYS;
            for (int sample = SAMPLES_PER_RAY; sample > 0; sample--) {
                double radius = sample * step;
                if (footprint.contains(centerX + (int)Math.round(Math.cos(angle) * radius),
                        centerZ + (int)Math.round(Math.sin(angle) * radius))) {
                    raw[ray] = radius + step;
                    break;
                }
            }
        }
        // Dilate one angular sample to keep fragments out of narrow native coast indentations.
        double[] outline = new double[RAYS];
        for (int i = 0; i < RAYS; i++)
            outline[i] = Math.max(raw[i], Math.max(raw[(i + RAYS - 1) % RAYS], raw[(i + 1) % RAYS]));
        return new OceanPerimeter(outline);
    }

    /** Negative inside the sampled outer silhouette, including internal channels and reef gaps. */
    public double outsideDistance(double x, double z) {
        double ray = (Math.atan2(z, x) / (Math.PI * 2) + 1) * RAYS;
        int index = (int)Math.floor(ray);
        double fraction = ray - index;
        double a = radii[index % RAYS], b = radii[(index + 1) % RAYS];
        if (a == 0 || b == 0) return Double.POSITIVE_INFINITY;
        return Math.hypot(x, z) - (a + (b - a) * fraction);
    }
}
