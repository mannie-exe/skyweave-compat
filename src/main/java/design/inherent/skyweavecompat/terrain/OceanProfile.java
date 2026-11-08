package design.inherent.skyweavecompat.terrain;

/** Optional edge treatments of native wet islands; no independent basin generator. */
public record OceanProfile(boolean enabled, double admissionChance,
        double lagoonWeight, double reefRingWeight, double deadOceanShelfWeight, double brokenReefWeight,
        double coastalChance, double perimeterChance,
        int coveOpeningDegrees, int coveShoulderDegrees, int outletDepth, int sealThickness,
        boolean spireCompanions, int spireClearance) {
    public enum Mode { NATIVE, COASTAL, PERIMETER }
    public static final OceanProfile INITIAL = new OceanProfile(true, 0.20, 1, 0, 0, 1, 0.25, 0, 100, 20, 2, 4, true, 16);
    public OceanProfile {
        if (!Double.isFinite(admissionChance) || admissionChance < 0 || admissionChance > 1
                || !weight(lagoonWeight) || !weight(reefRingWeight) || !weight(deadOceanShelfWeight) || !weight(brokenReefWeight)
                || !Double.isFinite(coastalChance) || !Double.isFinite(perimeterChance)
                || coastalChance < 0 || perimeterChance < 0 || coastalChance + perimeterChance > 1
                || coveOpeningDegrees < 30 || coveOpeningDegrees > 180
                || coveShoulderDegrees < 1 || coveShoulderDegrees * 2 >= coveOpeningDegrees
                || outletDepth < 1 || outletDepth > 8
                || sealThickness < 3 || sealThickness > 16 || spireClearance < 0 || spireClearance > 64)
            throw new IllegalArgumentException("Invalid native ocean edge profile");
    }
    private static boolean weight(double value) { return Double.isFinite(value) && value >= 0 && value <= 10; }
    public boolean admits(long seed) {
        return enabled && unit(seed ^ 0x607b1c192a8e34d5L) < admissionChance;
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
