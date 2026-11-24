package design.inherent.skyweavecompat.terrain;

import java.util.function.Supplier;

/** Bounded planning reads bypass later stages and the final-column cache. */
public enum TerrainStage {
    EXTERIOR, INTERIOR, COMPLETE;

    private static final ThreadLocal<TerrainStage> ACTIVE = ThreadLocal.withInitial(() -> COMPLETE);

    public static TerrainStage current() { return ACTIVE.get(); }

    public <T> T query(Supplier<T> query) {
        var previous = ACTIVE.get();
        if (previous == this) return query.get();
        ACTIVE.set(this);
        try { return query.get(); }
        // This slot holds only an enum singleton, never a query or world owner.
        // Removing COMPLETE recreated a ThreadLocal entry at every outer read.
        finally { ACTIVE.set(previous); }
    }
}
