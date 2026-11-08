package design.inherent.skyweavecompat.climate;

/** Carries the owning surface pass into upstream selectors that have no world argument. */
public final class SurfaceClimateScope {
    private static final ThreadLocal<Boolean> ACTIVE = new ThreadLocal<>();
    private SurfaceClimateScope() {}
    public static boolean active() { return Boolean.TRUE.equals(ACTIVE.get()); }
    public static void run(boolean adapted, Runnable operation) {
        query(adapted, () -> { operation.run(); return null; });
    }
    public static <T> T query(boolean adapted, java.util.function.Supplier<T> operation) {
        Boolean previous = ACTIVE.get();
        ACTIVE.set(adapted);
        try { return operation.get(); }
        finally {
            if (previous == null) ACTIVE.remove(); else ACTIVE.set(previous);
        }
    }
}
