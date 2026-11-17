package design.inherent.skyweavecompat.rendering;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Conservative X/Z ray footprint from the camera cell to the complete construct extent. */
public record CoverageFootprint(List<Cell> cells, Status status) {
    public static final int CELL_SIZE = 16;
    public enum Status { VALID, INVALID, TOO_LARGE }
    public record Cell(int x, int z) {}
    private record Point(double x, double z) {}

    public CoverageFootprint { cells = List.copyOf(cells); }

    public static CoverageFootprint create(double cameraX, double cameraZ,
            double minimumX, double minimumZ, double maximumX, double maximumZ, int limit) {
        if (limit < 1 || limit > 16384) throw new IllegalArgumentException("Coverage cell limit must be 1..16384");
        for (double coordinate : new double[]{cameraX, cameraZ, minimumX, minimumZ, maximumX, maximumZ})
            if (!Double.isFinite(coordinate) || Math.abs(coordinate) > 30_000_000)
                return new CoverageFootprint(List.of(), Status.INVALID);
        if (minimumX > maximumX || minimumZ > maximumZ) return new CoverageFootprint(List.of(), Status.INVALID);
        var points = new ArrayList<Point>(8);
        rectangle(points, cell(cameraX), cell(cameraZ), cell(cameraX), cell(cameraZ));
        rectangle(points, cell(minimumX), cell(minimumZ), cell(maximumX), cell(maximumZ));
        var hull = hull(points);
        int firstRow = hull.stream().mapToInt(p -> cell(p.z)).min().orElseThrow();
        int lastRow = hull.stream().mapToInt(p -> cell(p.z)).max().orElseThrow();
        var cells = new ArrayList<Cell>();
        for (int z = firstRow; z <= lastRow; z++) {
            double lowZ = (double) z * CELL_SIZE, highZ = lowZ + CELL_SIZE;
            double lowX = Double.POSITIVE_INFINITY, highX = Double.NEGATIVE_INFINITY;
            for (int edge = 0; edge < hull.size(); edge++) {
                var a = hull.get(edge); var b = hull.get((edge + 1) % hull.size());
                if (a.z >= lowZ && a.z <= highZ) { lowX = Math.min(lowX, a.x); highX = Math.max(highX, a.x); }
                if (a.z == b.z) continue;
                for (double boundary : new double[]{lowZ, highZ}) {
                    double t = (boundary - a.z) / (b.z - a.z);
                    if (t < 0 || t > 1) continue;
                    double x = a.x + t * (b.x - a.x);
                    lowX = Math.min(lowX, x); highX = Math.max(highX, x);
                }
            }
            if (lowX > highX) continue;
            int firstColumn = cell(lowX), lastColumn = cell(highX);
            if ((long) cells.size() + lastColumn - firstColumn + 1 > limit)
                return new CoverageFootprint(List.of(), Status.TOO_LARGE);
            for (int x = firstColumn; x <= lastColumn; x++) cells.add(new Cell(x, z));
        }
        return new CoverageFootprint(cells, Status.VALID);
    }

    private static int cell(double block) { return (int) Math.floor(block / CELL_SIZE); }
    private static void rectangle(List<Point> points, int x0, int z0, int x1, int z1) {
        double a = (double) x0 * CELL_SIZE, b = (double) z0 * CELL_SIZE;
        double c = ((double) x1 + 1) * CELL_SIZE, d = ((double) z1 + 1) * CELL_SIZE;
        points.add(new Point(a, b)); points.add(new Point(c, b)); points.add(new Point(c, d)); points.add(new Point(a, d));
    }
    private static List<Point> hull(List<Point> input) {
        var points = input.stream().distinct().sorted(Comparator.comparingDouble(Point::x).thenComparingDouble(Point::z)).toList();
        var lower = new ArrayList<Point>(); var upper = new ArrayList<Point>();
        for (var point : points) add(lower, point);
        for (int i = points.size() - 1; i >= 0; i--) add(upper, points.get(i));
        lower.removeLast(); upper.removeLast(); lower.addAll(upper); return lower;
    }
    private static void add(ArrayList<Point> hull, Point point) {
        while (hull.size() >= 2) {
            var a = hull.get(hull.size() - 2); var b = hull.getLast();
            if ((b.x - a.x) * (point.z - a.z) - (b.z - a.z) * (point.x - a.x) > 0) break;
            hull.removeLast();
        }
        hull.add(point);
    }
}
