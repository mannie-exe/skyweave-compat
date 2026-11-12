package design.inherent.skyweavecompat.climate;

/** Carries the owning surface pass into upstream selectors that have no world argument. */
public final class SurfaceClimateScope {
    private record Context(boolean active, net.minecraft.world.level.LevelReader level) {}
    private static final ThreadLocal<Context> ACTIVE = new ThreadLocal<>();
    private SurfaceClimateScope() {}
    public static boolean active() { var context = ACTIVE.get(); return context != null && context.active(); }
    public static net.minecraft.world.level.LevelReader level() { var context = ACTIVE.get(); return context == null ? null : context.level(); }
    public static void run(boolean adapted, Runnable operation) {
        query(adapted, () -> { operation.run(); return null; });
    }
    public static <T> T query(boolean adapted, java.util.function.Supplier<T> operation) {
        return query(new Context(adapted,null),operation);
    }
    public static void run(net.minecraft.world.level.LevelReader level, Runnable operation) {
        query(level, () -> { operation.run(); return null; });
    }
    public static <T> T query(net.minecraft.world.level.LevelReader level, java.util.function.Supplier<T> operation) {
        return query(new Context(IslandClimate.applies(level),level),operation);
    }
    private static <T> T query(Context context, java.util.function.Supplier<T> operation) {
        var previous = ACTIVE.get();
        ACTIVE.set(context);
        try { return operation.get(); }
        finally {
            if (previous == null) ACTIVE.remove(); else ACTIVE.set(previous);
        }
    }
}
