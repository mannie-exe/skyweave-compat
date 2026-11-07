package design.inherent.skyweavecompat.terrain;

import java.util.*;
import java.util.function.Predicate;

/** Finite, resumable metadata traversal; owns no world, chunk tickets or generation state. */
public final class IslandSearch<T> {
    public static final int MAX_CELLS = 65536;
    public static final int MAX_RESULTS = 64;
    private final int minX, minZ, width, depth;
    private final Comparator<T> order;
    private final ArrayList<T> results = new ArrayList<>();
    private int visited;
    private long matches;

    public IslandSearch(int originX, int originZ, int radius, int spacing, int halo, Comparator<T> order) {
        if (radius < 1 || radius > 131072 || spacing < 1 || halo < 0 || halo > 16)
            throw new IllegalArgumentException("Invalid search bounds");
        minX = Math.toIntExact(Math.floorDiv((long)originX - radius, spacing) - halo);
        minZ = Math.toIntExact(Math.floorDiv((long)originZ - radius, spacing) - halo);
        width = Math.toIntExact(Math.floorDiv((long)originX + radius, spacing) + halo - minX + 1);
        depth = Math.toIntExact(Math.floorDiv((long)originZ + radius, spacing) + halo - minZ + 1);
        if ((long)width * depth > MAX_CELLS) throw new IllegalArgumentException("Search exceeds 65536 cells; reduce radius");
        this.order = order;
    }
    public ClusterLayout.Cell next() {
        if (complete()) throw new NoSuchElementException("Search complete");
        int index = visited++;
        return new ClusterLayout.Cell(minX + index / depth, minZ + index % depth);
    }
    public void accept(Collection<T> candidates, Predicate<T> matches) {
        for (T candidate : candidates) if (matches.test(candidate)) {
            this.matches++; results.add(candidate); results.sort(order);
            if (results.size() > MAX_RESULTS) results.removeLast();
        }
    }
    public boolean complete() { return visited == total(); }
    public int visited() { return visited; }
    public int total() { return width * depth; }
    public long matches() { return matches; }
    public List<T> results() { return List.copyOf(results); }
}
