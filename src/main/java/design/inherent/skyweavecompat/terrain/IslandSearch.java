package design.inherent.skyweavecompat.terrain;

import java.util.*;
import java.util.function.Predicate;

/** Finite, resumable metadata traversal; owns no world, chunk tickets or generation state. */
public final class IslandSearch<T> {
    public static final int MAX_CELLS = 65536;
    public static final int MAX_RESULTS = 64;
    private final List<ClusterLayout.Cell> cells;
    private final Comparator<T> order;
    private final ArrayList<T> results = new ArrayList<>();
    private int visited;
    private long matches;

    public IslandSearch(int originX, int originZ, int radius, int spacing, int halo, Comparator<T> order) {
        if (radius < 1 || radius > 131072 || spacing < 1 || halo < 0 || halo > 16)
            throw new IllegalArgumentException("Invalid search bounds");
        int minX = Math.toIntExact(Math.floorDiv((long)originX - radius, spacing) - halo);
        int minZ = Math.toIntExact(Math.floorDiv((long)originZ - radius, spacing) - halo);
        int width = Math.toIntExact(Math.floorDiv((long)originX + radius, spacing) + halo - minX + 1);
        int depth = Math.toIntExact(Math.floorDiv((long)originZ + radius, spacing) + halo - minZ + 1);
        if ((long)width * depth > MAX_CELLS) throw new IllegalArgumentException("Search exceeds 65536 cells; reduce radius");
        var ordered = new ArrayList<ClusterLayout.Cell>(width * depth);
        for (int x = 0; x < width; x++) for (int z = 0; z < depth; z++)
            ordered.add(new ClusterLayout.Cell(Math.addExact(minX, x), Math.addExact(minZ, z)));
        // Cell anchors only prioritize work; jittered island results still use their
        // real radius/distance filter and the complete halo domain is retained.
        ordered.sort(Comparator.<ClusterLayout.Cell>comparingDouble(cell -> {
            double dx = (double)cell.x() * spacing + spacing / 2.0 - originX,
                    dz = (double)cell.z() * spacing + spacing / 2.0 - originZ;
            return dx * dx + dz * dz;
        }).thenComparingInt(ClusterLayout.Cell::x).thenComparingInt(ClusterLayout.Cell::z));
        cells = List.copyOf(ordered);
        this.order = order;
    }
    public ClusterLayout.Cell next() {
        if (complete()) throw new NoSuchElementException("Search complete");
        return cells.get(visited++);
    }
    public void accept(Collection<T> candidates, Predicate<T> matches) {
        for (T candidate : candidates) if (matches.test(candidate)) {
            this.matches++; results.add(candidate); results.sort(order);
            if (results.size() > MAX_RESULTS) results.removeLast();
        }
    }
    public boolean complete() { return visited == total(); }
    public int visited() { return visited; }
    public int total() { return cells.size(); }
    public long matches() { return matches; }
    public List<T> results() { return List.copyOf(results); }
}
