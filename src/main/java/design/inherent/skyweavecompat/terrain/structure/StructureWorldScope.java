package design.inherent.skyweavecompat.terrain.structure;

import java.lang.ref.WeakReference;
import java.util.function.Supplier;

/** Native coordination follows the generator, including unmodified structures in the same placement pass. */
public final class StructureWorldScope {
    private record Frame(StructureDistribution distribution) {}
    private static final ThreadLocal<Frame> ACTIVE = new ThreadLocal<>();
    private StructureWorldScope() {}

    public static StructureDistribution current() {
        var frame = ACTIVE.get();
        if (frame != null) return frame.distribution();
        var attempt = StructurePlacementScope.current();
        return attempt == null ? null : attempt.distribution();
    }

    public static <T> T run(StructureDistribution distribution, Supplier<T> action) {
        var previous = ACTIVE.get();
        ACTIVE.set(new Frame(distribution));
        try { return action.get(); }
        finally { if (previous == null) ACTIVE.remove(); else ACTIVE.set(previous); }
    }

    /** Pending delivery may revoke a placement after its original synchronous pass has ended. */
    public static Runnable bind(Runnable action) {
        var owner = current();
        if (owner == null) return () -> run(null, () -> { action.run(); return null; });
        var reference = new WeakReference<>(owner);
        return () -> {
            var live = reference.get();
            if (live != null) run(live, () -> { action.run(); return null; });
        };
    }
}
