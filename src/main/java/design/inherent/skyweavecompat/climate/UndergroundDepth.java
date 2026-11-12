package design.inherent.skyweavecompat.climate;

/** Native climate-depth units, independent of physical Y and geological resource budgets. */
public final class UndergroundDepth {
    private UndergroundDepth() {}

    /** Surface zero, native Deep Dark target 1.1 at the natural exterior bottom. */
    public static double sample(int y, int bottom, int top, double bottomDepth) {
        if (top <= bottom || !Double.isFinite(bottomDepth) || bottomDepth <= 0 || bottomDepth > 2)
            throw new IllegalArgumentException("Invalid underground depth frame");
        return Math.clamp(((double)top - y) / ((double)top - bottom), 0, 1) * bottomDepth;
    }
}
