package design.inherent.skyweavecompat.terrain.structure;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.function.ToIntFunction;

/** Foundation coverage and unsupported depth; a long shallow overhang is not a deep void span. */
public final class FoundationSupport {
    public static final int MISSING = Integer.MIN_VALUE;
    public record Foot(int x, int y, int z) {}
    public record Result(boolean accepted, int columns, int supported, int unsupportedDepth) {}
    private FoundationSupport() {}

    public static Result evaluate(Collection<Foot> feet, ToIntFunction<Foot> ground, VillageFitSettings settings) {
        return evaluate(feet,ground,settings,false);
    }
    /** Required roots cannot borrow coverage from neighboring columns; roads may retain bounded overhangs. */
    public static Result evaluate(Collection<Foot> feet, ToIntFunction<Foot> ground, VillageFitSettings settings,boolean requireComplete) {
        var supported = new HashSet<Long>();
        var columns = new HashSet<Long>();
        for (var foot : feet) {
            long key = key(foot.x(), foot.z()); columns.add(key);
            int top = ground.applyAsInt(foot);
            long gap = (long)foot.y() - 1 - top;
            if (top != MISSING && gap >= -1 && gap <= settings.maximumGroundGap()) supported.add(key);
        }
        if (columns.isEmpty() || supported.size() < columns.size() * settings.minimumSupportFraction()
                || requireComplete && supported.size()!=columns.size())
            return new Result(false, columns.size(), supported.size(), Integer.MAX_VALUE);
        if(requireComplete)return new Result(true,columns.size(),supported.size(),0);
        var distance = new HashMap<Long, Integer>();
        var queue = new ArrayDeque<Long>();
        supported.forEach(key -> { distance.put(key, 0); queue.add(key); });
        while (!queue.isEmpty()) {
            long cell = queue.removeFirst(); int next = distance.get(cell) + 1;
            int x = (int)(cell >> 32), z = (int)cell;
            for (long neighbor : new long[]{key(x-1,z),key(x+1,z),key(x,z-1),key(x,z+1)})
                if (columns.contains(neighbor) && !distance.containsKey(neighbor)) {
                    distance.put(neighbor, next); queue.addLast(neighbor);
                }
        }
        int depth = columns.stream().mapToInt(key -> distance.getOrDefault(key, Integer.MAX_VALUE)).max().orElse(Integer.MAX_VALUE);
        return new Result(depth <= settings.maximumUnsupportedDepth(), columns.size(), supported.size(), depth);
    }
    public static long key(int x, int z) { return (long)x << 32 | z & 0xffffffffL; }
}
