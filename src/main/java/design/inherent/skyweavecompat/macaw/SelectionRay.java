package design.inherent.skyweavecompat.macaw;

/** Geometry guards for a secondary ray scan; a foreign-frame hit must never extend its segment. */
public final class SelectionRay {
    private SelectionRay() {}

    public static boolean contains(double dx, double dy, double dz, double hx, double hy, double hz) {
        double lengthSquared = dx * dx + dy * dy + dz * dz;
        if (!Double.isFinite(lengthSquared) || lengthSquared == 0) return false;
        double t = (hx * dx + hy * dy + hz * dz) / lengthSquared;
        if (!Double.isFinite(t) || t < 0 || t > 1) return false;
        double ex = hx - t * dx, ey = hy - t * dy, ez = hz - t * dz;
        double errorSquared = ex * ex + ey * ey + ez * ez;
        return Double.isFinite(errorSquared) && errorSquared <= 1e-12;
    }

    /** A DDA crosses each axis plane at most once; padding covers boundary epsilon and the first cell. */
    public static long cellBudget(double fromX, double fromY, double fromZ, double toX, double toY, double toZ) {
        double total = Math.abs(Math.floor(fromX) - Math.floor(toX))
                + Math.abs(Math.floor(fromY) - Math.floor(toY)) + Math.abs(Math.floor(fromZ) - Math.floor(toZ));
        return Double.isFinite(total) && total < Long.MAX_VALUE - 4 ? (long)total + 4 : 0;
    }
}
