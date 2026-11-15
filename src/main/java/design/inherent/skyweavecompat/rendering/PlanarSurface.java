package design.inherent.skyweavecompat.rendering;

import java.util.ArrayList;
import java.util.List;

/** Bounded convex triangle/quad contact math in final block-local coordinates. */
public record PlanarSurface(List<Point> points, Point normal, double distance, int axis) {
    public static final double TOLERANCE = 1.0 / 32768;
    public record Point(double x, double y, double z) {
        public Point {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
                throw new IllegalArgumentException("Non-finite geometry");
        }
        public double coordinate(int a) { return a == 0 ? x : a == 1 ? y : z; }
        public Point plus(Point p) { return new Point(x + p.x, y + p.y, z + p.z); }
        public Point times(double v) { return new Point(x * v, y * v, z * v); }
        public Point minus(Point p) { return plus(p.times(-1)); }
        public double dot(Point p) { return x * p.x + y * p.y + z * p.z; }
        public Point cross(Point p) { return new Point(y * p.z - z * p.y, z * p.x - x * p.z, x * p.y - y * p.x); }
    }
    public record Part(List<Point> points, boolean shared) {
        public Part { points = List.copyOf(points); }
    }
    public PlanarSurface { points = List.copyOf(points); }

    public static PlanarSurface of(List<Point> input, int facingAxis, int sign) {
        if (input.size() != 4 || facingAxis < 0 || facingAxis > 2 || Math.abs(sign) != 1) return null;
        var vertices = new ArrayList<Point>(4);
        for (var p : input) {
            if (Math.max(Math.abs(p.x), Math.max(Math.abs(p.y), Math.abs(p.z))) > 4) return null;
            if (vertices.isEmpty() || p.minus(vertices.getLast()).dot(p.minus(vertices.getLast())) > 1e-14) vertices.add(p);
        }
        if (vertices.size() > 1 && vertices.getFirst().minus(vertices.getLast()).dot(vertices.getFirst().minus(vertices.getLast())) < 1e-14)
            vertices.removeLast();
        if (vertices.size() < 3) return null;
        Point n = new Point(0, 0, 0); double magnitude = 0;
        for (int i = 1; i + 1 < vertices.size() && magnitude < 1e-10; i++) {
            n = vertices.get(i).minus(vertices.getFirst()).cross(vertices.get(i + 1).minus(vertices.getFirst()));
            magnitude = Math.sqrt(n.dot(n));
        }
        if (magnitude < 1e-10) return null;
        n = n.times(1 / magnitude);
        if (n.coordinate(facingAxis) * sign < 0) n = n.times(-1);
        double d = n.dot(vertices.getFirst());
        for (var p : vertices) if (Math.abs(n.dot(p) - d) > TOLERANCE) return null;
        int axis = Math.abs(n.x) > Math.abs(n.y) ? 0 : 1;
        if (Math.abs(n.z) > Math.abs(n.coordinate(axis))) axis = 2;
        double area = area(vertices, axis);
        if (Math.abs(area) < 1e-10) return null;
        double winding = Math.signum(area);
        for (int i = 0; i < vertices.size(); i++)
            if (cross(vertices.get(i), vertices.get((i + 1) % vertices.size()), vertices.get((i + 2) % vertices.size()), axis) * winding < -TOLERANCE)
                return null;
        return new PlanarSurface(vertices, n, d, axis);
    }

    public PlanarSurface translated(Point delta) {
        return new PlanarSurface(points.stream().map(p -> p.plus(delta)).toList(), normal, distance + normal.dot(delta), axis);
    }
    public boolean oppositePlane(PlanarSurface other) {
        return normal.dot(other.normal) < -1 + 1e-7 && Math.abs(distance + other.distance) <= TOLERANCE;
    }

    /** Returns null on budget exhaustion, never a partially credited partition. */
    public List<Part> partition(List<PlanarSurface> contacts) {
        if (contacts.size() > 512) return null;
        List<Part> result = List.of(new Part(points, false));
        for (var contact : contacts) {
            if (!oppositePlane(contact)) continue;
            double winding = Math.signum(area(contact.points, axis));
            if (winding == 0) continue;
            var next = new ArrayList<Part>();
            for (var part : result) {
                if (part.shared) { next.add(part); continue; }
                List<Point> remaining = part.points;
                for (int i = 0; i < contact.points.size() && !remaining.isEmpty(); i++) {
                    Point a = contact.points.get(i), b = contact.points.get((i + 1) % contact.points.size());
                    List<Point> outside = clip(remaining, a, b, axis, -winding);
                    if (!outside.isEmpty()) next.add(new Part(outside, false));
                    remaining = clip(remaining, a, b, axis, winding);
                }
                if (!remaining.isEmpty()) next.add(new Part(remaining, true));
                if (next.size() > 256) return null;
            }
            result = next;
        }
        return List.copyOf(result);
    }

    private static List<Point> clip(List<Point> source, Point a, Point b, int axis, double winding) {
        var out = new ArrayList<Point>();
        Point previous = source.getLast(); double pd = cross(a, b, previous, axis) * winding;
        for (var current : source) {
            double cd = cross(a, b, current, axis) * winding;
            boolean pin = pd >= 0, cin = cd >= 0;
            if (pin != cin) out.add(previous.plus(current.minus(previous).times(pd / (pd - cd))));
            if (cin) out.add(current);
            previous = current; pd = cd;
        }
        // Boundary vertices can be produced both as an intersection and as an admitted endpoint.
        for (int i = out.size() - 1; i >= 0 && out.size() > 1; i--) {
            Point p = out.get(i), q = out.get((i + 1) % out.size());
            if (p.minus(q).dot(p.minus(q)) < 1e-14) out.remove(i);
        }
        boolean changed = true;
        while (changed && out.size() > 3) {
            changed = false;
            for (int i = 0; i < out.size(); i++) if (Math.abs(cross(out.get((i + out.size() - 1) % out.size()),
                    out.get(i), out.get((i + 1) % out.size()), axis)) < 1e-12) {
                out.remove(i); changed = true; break;
            }
        }
        if (out.size() < 3 || Math.abs(area(out, axis)) < 1e-10) return List.of();
        return out;
    }

    public static double area(List<Point> points, int axis) {
        double sum = 0; int u = (axis + 1) % 3, v = (axis + 2) % 3;
        for (int i = 0; i < points.size(); i++) {
            var a = points.get(i); var b = points.get((i + 1) % points.size());
            sum += a.coordinate(u) * b.coordinate(v) - b.coordinate(u) * a.coordinate(v);
        }
        return sum / 2;
    }
    private static double cross(Point a, Point b, Point p, int axis) {
        int u = (axis + 1) % 3, v = (axis + 2) % 3;
        return (b.coordinate(u) - a.coordinate(u)) * (p.coordinate(v) - a.coordinate(v))
                - (b.coordinate(v) - a.coordinate(v)) * (p.coordinate(u) - a.coordinate(u));
    }

    /** Fan-triangle weights, matching BLOCK-quad interpolation for planar nonrectangular output. */
    public double[] weights(Point p) {
        int u = (axis + 1) % 3, v = (axis + 2) % 3;
        for (int i = 1; i + 1 < points.size(); i++) {
            Point a = points.getFirst(), b = points.get(i), c = points.get(i + 1);
            double divisor = cross(a, b, c, axis);
            if (Math.abs(divisor) < 1e-12) continue;
            double wb = ((p.coordinate(u) - a.coordinate(u)) * (c.coordinate(v) - a.coordinate(v))
                    - (p.coordinate(v) - a.coordinate(v)) * (c.coordinate(u) - a.coordinate(u))) / divisor;
            double wc = ((b.coordinate(u) - a.coordinate(u)) * (p.coordinate(v) - a.coordinate(v))
                    - (b.coordinate(v) - a.coordinate(v)) * (p.coordinate(u) - a.coordinate(u))) / divisor;
            if (wb >= -1e-6 && wc >= -1e-6 && wb + wc <= 1 + 1e-6) {
                double[] result = new double[4]; result[0] = 1 - wb - wc; result[i] = wb; result[i + 1] = wc;
                return result;
            }
        }
        throw new IllegalArgumentException("Point outside source polygon");
    }
}
