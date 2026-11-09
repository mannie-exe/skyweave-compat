package design.inherent.skyweavecompat.terrain.structure;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/** Pure selection. Recomputing after eviction, restarts, or another visitation order gives the same result. */
public final class StructureSelection {
    public record Owner(int cellX, int cellZ) {}
    public record Site(int chunkX, int chunkZ) {}
    private StructureSelection() {}

    /** One budget owner for native fitting and conservative lookup rejection. */
    public static long attemptLimit(StructureFamily family, int allowance) {
        if (allowance < 0) throw new IllegalArgumentException("Negative structure allowance");
        return allowance * (family == StructureFamily.VILLAGES ? 3L : 8L);
    }

    /** Shared conservative prefix for generation, native lookup and command prechecks. */
    public static boolean withinAttempts(StructureFamily family, int allowance, List<Site> ranked, Site site) {
        int index = ranked.indexOf(site);
        return index >= 0 && index < attemptLimit(family, allowance);
    }

    public static int allowance(long worldSeed, Owner owner, StructureFamily family,
            double eligibleChunks, StructurePolicy policy) {
        if (!Double.isFinite(eligibleChunks) || eligibleChunks < 0)
            throw new IllegalArgumentException("Eligible area must be finite and nonnegative");
        var rule = policy.rule(family);
        double expected = Math.min(rule.maximumPerCluster(),
                eligibleChunks / 1024 * rule.targetStartsPer1024Chunks() * policy.frequencyMultiplier());
        int whole = (int)Math.floor(expected);
        return whole + (unit(seed(worldSeed, owner, family) ^ 0x684f3a90c81742edL) < expected - whole ? 1 : 0);
    }

    public static List<Site> ranked(long worldSeed, Owner owner, StructureFamily family, Collection<Site> sites) {
        long seed = seed(worldSeed, owner, family);
        Comparator<Site> order = (first, second) -> Long.compareUnsigned(rank(seed, first), rank(seed, second));
        return sites.stream().distinct().sorted(order.thenComparingInt(Site::chunkX).thenComparingInt(Site::chunkZ)).toList();
    }

    public static boolean outpostExcluded(Site outpost, Collection<Site> villages, int separationChunks) {
        if (separationChunks < 0 || separationChunks > 64) throw new IllegalArgumentException("Invalid outpost separation");
        if (separationChunks == 0) return false;
        return villages.stream().anyMatch(village -> Math.abs((long)outpost.chunkX - village.chunkX) <= separationChunks
                && Math.abs((long)outpost.chunkZ - village.chunkZ) <= separationChunks);
    }

    private static long seed(long worldSeed, Owner owner, StructureFamily family) {
        return mix(worldSeed ^ (long)owner.cellX * 0x9e3779b97f4a7c15L
                ^ (long)owner.cellZ * 0xc2b2ae3d27d4eb4fL ^ mix(family.key.hashCode()));
    }
    private static long rank(long seed, Site site) {
        return mix(seed ^ (long)site.chunkX * 0x165667b19e3779f9L ^ (long)site.chunkZ * 0x85ebca77c2b2ae63L);
    }
    private static double unit(long value) { return (mix(value) >>> 11) * 0x1.0p-53; }
    private static long mix(long value) {
        value = (value ^ value >>> 30) * 0xbf58476d1ce4e5b9L;
        value = (value ^ value >>> 27) * 0x94d049bb133111ebL;
        return value ^ value >>> 31;
    }
}
