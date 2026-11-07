package design.inherent.skyweavecompat.terrain;

/** Bounded local Voronoi remnants. Geometry has no ocean surface or decoration semantics. */
public final class FragmentedExterior {
    public record Sample(boolean exists, double heightChoice, double conservativeClearance) {}
    public static Sample sample(long seed, double x, double z, ShapeProfile.Fractures profile) {
        int step = profile.cellSize(), gx = (int)Math.floor(x / step), gz = (int)Math.floor(z / step);
        double first = Double.POSITIVE_INFINITY, second = Double.POSITIVE_INFINITY;
        double ax = 0, az = 0, bx = 0, bz = 0; int ix = 0, iz = 0, jx = 0, jz = 0;
        // A two-cell halo includes the two nearest centers under the bounded quarter-cell jitter.
        for (int dz = -2; dz <= 2; dz++) for (int dx = -2; dx <= 2; dx++) {
            int cx = gx + dx, cz = gz + dz; long key = key(seed, cx, cz);
            double px = (cx + .5 + (IslandStyles.unit(key, 1) - .5) * .5) * step;
            double pz = (cz + .5 + (IslandStyles.unit(key, 2) - .5) * .5) * step;
            double distance = (px - x) * (px - x) + (pz - z) * (pz - z);
            if (distance < first) {
                second = first; bx = ax; bz = az; jx = ix; jz = iz;
                first = distance; ax = px; az = pz; ix = cx; iz = cz;
            } else if (distance < second) { second = distance; bx = px; bz = pz; jx = cx; jz = cz; }
        }
        int groupX = Math.floorDiv(ix, 2), groupZ = Math.floorDiv(iz, 2);
        long group = key(seed ^ 0x39ab2cL, groupX, groupZ);
        boolean joined = IslandStyles.unit(group, 3) < .35;
        long remnant = joined ? group : key(seed, ix, iz);
        boolean core = Math.hypot(ax, az) < step * .8;
        if (!core && IslandStyles.unit(remnant, 4) < profile.removalChance()) return new Sample(false, .5, 0);
        double separation = Math.hypot(ax - bx, az - bz);
        double boundary = (second - first) / (2 * separation);
        boolean sameJoinedGroup = joined && groupX == Math.floorDiv(jx, 2) && groupZ == Math.floorDiv(jz, 2);
        if (!core && !sameJoinedGroup && boundary < profile.gapWidth() * .5) return new Sample(false, .5, 0);
        // Half the nearest-distance difference is a conservative lower bound to every
        // cell bisector, even when the nearest internal face belongs to a joined group.
        double clearance = Math.max(0, (Math.sqrt(second) - Math.sqrt(first)) * .5 - profile.gapWidth() * .5);
        return new Sample(true, core ? .5 : IslandStyles.unit(remnant, 5), clearance);
    }
    private static long key(long seed, int x, int z) {
        return seed ^ x * 0x9e3779b97f4a7c15L ^ z * 0x632be59bd9b4e019L;
    }
    private FragmentedExterior() {}
}
