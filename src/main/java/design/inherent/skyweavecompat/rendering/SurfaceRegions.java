package design.inherent.skyweavecompat.rendering;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/** Rectangular coverage in a face's tangent plane; contains no game or renderer APIs. */
public final class SurfaceRegions {
    private SurfaceRegions() {}

    public record Rect(float u0, float v0, float u1, float v1) {
        public Rect {
            if (!Float.isFinite(u0 + v0 + u1 + v1) || u0 >= u1 || v0 >= v1)
                throw new IllegalArgumentException("Expected a finite positive rectangle");
        }
        public Rect intersection(Rect other) {
            float a = Math.max(u0, other.u0), b = Math.max(v0, other.v0);
            float c = Math.min(u1, other.u1), d = Math.min(v1, other.v1);
            return a < c && b < d ? new Rect(a, b, c, d) : null;
        }
        boolean contains(float u, float v) { return u >= u0 && u <= u1 && v >= v0 && v <= v1; }
    }

    public record Part(Rect bounds, boolean shared) {}

    /** Partition once, retaining uncovered areas and merging cells of equal coverage. */
    public static List<Part> partition(Rect source, List<Rect> contacts) {
        var result = partitionChecked(source, contacts);
        return result == null ? List.of(new Part(source, false)) : result;
    }

    /** Null denotes an unqualified budget fallback, not successful uncovered geometry. */
    public static List<Part> partitionChecked(Rect source, List<Rect> contacts) {
        var clipped = new ArrayList<Rect>();
        var us = new TreeSet<Float>(); var vs = new TreeSet<Float>();
        us.add(source.u0); us.add(source.u1); vs.add(source.v0); vs.add(source.v1);
        for (Rect contact : contacts) {
            Rect overlap = source.intersection(contact);
            if (overlap == null) continue;
            if (overlap.equals(source)) return List.of(new Part(source, true));
            clipped.add(overlap);
            us.add(overlap.u0); us.add(overlap.u1); vs.add(overlap.v0); vs.add(overlap.v1);
        }
        if (clipped.isEmpty()) return List.of(new Part(source, false));
        var u = new ArrayList<>(us); var v = new ArrayList<>(vs);
        int width = u.size() - 1, height = v.size() - 1;
        // Unknown highly subdivided models retain ordinary backfaces rather than failing a mesh build.
        if ((long) width * height > 4096) return null;
        boolean[][] shared = new boolean[height][width], used = new boolean[height][width];
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            float a = (u.get(x) + u.get(x + 1)) / 2, b = (v.get(y) + v.get(y + 1)) / 2;
            for (Rect rect : clipped) if (rect.contains(a, b)) { shared[y][x] = true; break; }
        }
        var result = new ArrayList<Part>();
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            if (used[y][x]) continue;
            int right = x + 1, bottom = y + 1;
            while (right < width && !used[y][right] && shared[y][right] == shared[y][x]) right++;
            outer: while (bottom < height) {
                for (int i = x; i < right; i++)
                    if (used[bottom][i] || shared[bottom][i] != shared[y][x]) break outer;
                bottom++;
            }
            for (int j = y; j < bottom; j++) for (int i = x; i < right; i++) used[j][i] = true;
            result.add(new Part(new Rect(u.get(x), v.get(y), u.get(right), v.get(bottom)), shared[y][x]));
        }
        return List.copyOf(result);
    }
}
