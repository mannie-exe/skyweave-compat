package design.inherent.skyweavecompat.terrain;

/** Small periodic outline in local block coordinates; no world or chunk reads. */
public final class SkylightShape {
    private SkylightShape() {}
    public static boolean contains(long seed, double dx, double dz, InteriorProfile.Skylights profile) {
        double angle = OceanProfile.unit(seed ^ 0x742f6dba91L) * Math.PI * 2;
        double c = Math.cos(angle), s = Math.sin(angle);
        double aspect = 1 + (OceanProfile.unit(seed ^ 0xa71cb5L) * 2 - 1) * profile.aspectVariation();
        double x = (dx * c + dz * s) / aspect, z = (dz * c - dx * s) * aspect;
        double theta = Math.atan2(z, x);
        double phase = OceanProfile.unit(seed ^ 0x18a9ccf43L) * Math.PI * 2;
        double radius = profile.radius() * (0.85 + OceanProfile.unit(seed ^ 0x839ac91L) * 0.3);
        radius *= 1 + profile.warp() * (0.7 * Math.sin(theta * 3 + phase) + 0.3 * Math.sin(theta * 5 - phase));
        return x * x + z * z < radius * radius;
    }
    public static double bound(InteriorProfile.Skylights profile) {
        return profile.radius() * 1.15 * (1 + profile.warp()) / (1 - profile.aspectVariation());
    }
}
