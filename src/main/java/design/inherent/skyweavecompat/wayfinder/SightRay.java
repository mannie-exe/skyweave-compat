package design.inherent.skyweavecompat.wayfinder;

/** A normalized sight ray and resumable grid traversal, independent of game APIs. */
public record SightRay(double x, double y, double z, double dx, double dy, double dz) {
    public record Point(double x, double y, double z) {}
    public record Interval(double enter, double exit) {}

    public SightRay {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Double.isFinite(dx) || !Double.isFinite(dy) || !Double.isFinite(dz))
            throw new IllegalArgumentException("Sight ray must be finite");
        double length = Math.hypot(Math.hypot(dx, dy), dz);
        if (!Double.isFinite(length) || length < 1e-12) throw new IllegalArgumentException("Sight direction is empty");
        dx /= length; dy /= length; dz /= length;
    }

    public Point at(double distance) { return new Point(x + dx * distance, y + dy * distance, z + dz * distance); }

    /** Minecraft's lookup-table view vectors are not necessarily unit length. */
    public boolean sameDirection(double x, double y, double z) {
        double length = Math.hypot(Math.hypot(x,y),z);
        return Double.isFinite(length) && length > 1e-12
                && dx*(x/length)+dy*(y/length)+dz*(z/length) >= 1-1e-7;
    }

    /** Slab intersection, including origins outside the build envelope. No gameplay range cap. */
    public Interval intersect(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        if (!(minX < maxX && minY < maxY && minZ < maxZ)) throw new IllegalArgumentException("Empty bounds");
        double enter = 0, exit = Double.POSITIVE_INFINITY;
        double[] origin = {x, y, z}, direction = {dx, dy, dz};
        double[] low = {minX, minY, minZ}, high = {maxX, maxY, maxZ};
        for (int axis = 0; axis < 3; axis++) {
            if (direction[axis] == 0) {
                if (origin[axis] < low[axis] || origin[axis] >= high[axis]) return null;
            } else {
                double first = (low[axis] - origin[axis]) / direction[axis];
                double last = (high[axis] - origin[axis]) / direction[axis];
                enter = Math.max(enter, Math.min(first, last));
                exit = Math.min(exit, Math.max(first, last));
            }
        }
        return exit > enter && Double.isFinite(exit) ? new Interval(enter, exit) : null;
    }

    public Cursor cursor(Interval domain) { return new Cursor(this, domain); }

    public static final class Cursor {
        private final SightRay ray;
        private final double end;
        private double distance;
        private int x, y, z;
        private double nextX, nextY, nextZ;

        private Cursor(SightRay ray, Interval domain) {
            if (domain == null || !Double.isFinite(domain.enter()) || !Double.isFinite(domain.exit())
                    || domain.enter() < 0 || domain.enter() >= domain.exit())
                throw new IllegalArgumentException("Expected finite world intersection");
            this.ray = ray; distance = domain.enter(); end = domain.exit(); locate();
        }
        private static int cell(double position, double direction) {
            double inside = direction == 0 ? position : Math.nextAfter(position,
                    direction < 0 ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY);
            double floor = Math.floor(inside);
            if (floor < Integer.MIN_VALUE || floor > Integer.MAX_VALUE) throw new IllegalArgumentException("Grid overflow");
            return (int)floor;
        }
        private static double next(double origin, double direction, int cell) {
            return direction == 0 ? Double.POSITIVE_INFINITY
                    : ((direction > 0 ? (double)cell + 1 : cell) - origin) / direction;
        }
        private void locate() {
            var point = ray.at(distance);
            x = cell(point.x(), ray.dx); y = cell(point.y(), ray.dy); z = cell(point.z(), ray.dz);
            nextX = next(ray.x, ray.dx, x); nextY = next(ray.y, ray.dy, y); nextZ = next(ray.z, ray.dz, z);
        }
        public boolean complete() { return distance >= end; }
        public int x() { return x; }
        public int y() { return y; }
        public int z() { return z; }
        public double distance() { return distance; }
        public double end() { return end; }
        public double columnExit() { return Math.min(end, Math.min(nextX, nextZ)); }
        public double cellExit() { return Math.min(columnExit(), nextY); }

        public void advance() { advanceTo(cellExit()); }
        public void advanceColumn() { advanceTo(columnExit()); }
        private void advanceTo(double next) {
            // The ULP-sized step belongs to the next cell and prevents boundary stalls.
            distance = next >= end ? end : Math.nextUp(Math.max(distance, next));
            if (!complete()) locate();
        }
    }
}
