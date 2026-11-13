package design.inherent.skyweavecompat.climate;

/** Local presentation math only; no change to terrain, climate or shader-owned cloud layers. */
public final class CloudPlacement {
    public enum Mode { VANILLA, MAPPED, ABSOLUTE }
    private CloudPlacement() {}

    public static float height(Mode mode, float nativeHeight, int minimumY, int height,
            double offset, double absolute, int allowedMinimum, int allowedMaximum) {
        // NaN is the native no-cloud sentinel, even when Absolute is selected.
        if (Float.isNaN(nativeHeight)) return nativeHeight;
        if (!Float.isFinite(nativeHeight) || !Double.isFinite(offset) || !Double.isFinite(absolute)
                || allowedMinimum > allowedMaximum) throw new IllegalArgumentException("Invalid cloud height");
        double target = switch (mode) {
            case VANILLA -> nativeHeight + offset;
            case MAPPED -> EnvironmentHeight.cloudHeight(nativeHeight, minimumY, height) + offset;
            case ABSOLUTE -> absolute;
        };
        return (float) Math.clamp(target, allowedMinimum, allowedMaximum);
    }
}
