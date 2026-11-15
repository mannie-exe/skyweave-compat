package design.inherent.skyweavecompat.rendering;

/** Suppress only Compat's additions while a provider samples original material geometry. */
public final class GeometryScope implements AutoCloseable {
    private static final ThreadLocal<Integer> DEPTH = new ThreadLocal<>();
    private final Integer previous;
    private final Thread owner = Thread.currentThread();
    private boolean closed;

    private GeometryScope() {
        previous = DEPTH.get();
        DEPTH.set(previous == null ? 1 : previous + 1);
    }
    public static GeometryScope source() { return new GeometryScope(); }
    public static boolean sampling() { return DEPTH.get() != null; }
    @Override public void close() {
        if (closed || owner != Thread.currentThread() || DEPTH.get() == null
                || DEPTH.get() != (previous == null ? 1 : previous + 1)) throw new IllegalStateException("Geometry scope ownership/order");
        closed = true;
        if (previous == null) DEPTH.remove(); else DEPTH.set(previous);
    }
}
