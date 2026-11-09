package design.inherent.skyweavecompat.placement;

import java.util.*;
import java.util.function.Function;

/** Stable admission, independent of chunk visits, accepted-start counters and world mutations. */
public final class VillagePlan {
    public record Anchor(long seed, int x, int z, int radius, int surfaceY) {}
    public record Site(int chunkX, int chunkZ) {
        public int x() { return chunkX * 16 + 8; }
        public int z() { return chunkZ * 16 + 8; }
    }
    private VillagePlan() {}

    public static Anchor owner(Site site, Collection<Anchor> anchors) {
        return owner(site.x(), site.z(), anchors);
    }

    public static Anchor owner(int x, int z, Collection<Anchor> anchors) {
        return anchors.stream().min(Comparator.comparingLong((Anchor a) -> distanceSquared(x, z, a.x(), a.z()))
                .thenComparingInt(Anchor::x).thenComparingInt(Anchor::z)).orElse(null);
    }

    public static List<Site> select(Anchor anchor, Collection<Site> candidates,
            Function<Site, Anchor> ownership, int minimumGap, int maximumGap) {
        int target = VillageDistribution.forAnchor(anchor.seed());
        if (target == 0) return List.of();
        // The caller screens against the actual shaped body. A circular inner annulus would exclude
        // valid sites beside elliptical/crescent shores; complete footprints still pass NeighborRule.
        long minimum = minimumGap;
        long maximum = (long)anchor.radius() + maximumGap + 128;
        var eligible = candidates.stream().distinct().filter(site -> anchor.equals(ownership.apply(site)))
                .filter(site -> {
                    long d = distanceSquared(site.x(), site.z(), anchor.x(), anchor.z());
                    return d >= minimum * minimum && d <= maximum * maximum;
                }).sorted((a, b) -> {
                    int order = Long.compareUnsigned(rank(anchor.seed(), a), rank(anchor.seed(), b));
                    if (order != 0) return order;
                    order = Integer.compare(a.chunkX(), b.chunkX());
                    return order == 0 ? Integer.compare(a.chunkZ(), b.chunkZ()) : order;
                }).toList();
        var selected = new ArrayList<Site>(target);
        for (var site : eligible) {
            if (selected.stream().anyMatch(other -> distanceSquared(site.x(), site.z(), other.x(), other.z()) < 384L * 384)) continue;
            selected.add(site);
            if (selected.size() == target) break;
        }
        return List.copyOf(selected);
    }

    private static long rank(long seed, Site site) {
        long value = seed ^ (long)site.chunkX() * 0x9E3779B97F4A7C15L ^ (long)site.chunkZ() * 0xC2B2AE3D27D4EB4FL;
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    private static long distanceSquared(int x, int z, int otherX, int otherZ) {
        long dx = (long)x - otherX, dz = (long)z - otherZ;
        return dx * dx + dz * dz;
    }
}
