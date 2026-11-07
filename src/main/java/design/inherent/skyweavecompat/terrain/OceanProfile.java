package design.inherent.skyweavecompat.terrain;

/** Optional edge treatments of native wet islands; no independent basin generator. */
public record OceanProfile(boolean enabled, double coastalChance, double perimeterChance,
        int perimeterMinRadius, int perimeterMaxRadius, int outletWidth, int outletDepth, int sealThickness) {
    public enum Mode { NATIVE, COASTAL, PERIMETER }
    public static final OceanProfile INITIAL = new OceanProfile(true, 0.04, 0.01, 40, 64, 2, 2, 4);
    public OceanProfile {
        if (!Double.isFinite(coastalChance) || !Double.isFinite(perimeterChance)
                || coastalChance < 0 || perimeterChance < 0 || coastalChance + perimeterChance > 1
                || perimeterMinRadius < 16 || perimeterMaxRadius > 128 || perimeterMinRadius > perimeterMaxRadius
                || outletWidth < 1 || outletWidth > 16 || outletDepth < 1 || outletDepth > 8
                || sealThickness < 3 || sealThickness > 16)
            throw new IllegalArgumentException("Invalid native ocean edge profile");
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
