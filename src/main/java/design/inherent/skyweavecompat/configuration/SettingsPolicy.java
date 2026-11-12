package design.inherent.skyweavecompat.configuration;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Revisioned immutable runtime values owned by one live context and its execution thread. */
public final class SettingsPolicy<T> {
    /** Identity, not a permission or a transferable value hash. */
    public static final class Revision {
        private Revision() {}
    }
    public record Snapshot<T>(Revision revision, T value) {}
    public final class Prepared {
        private final Revision expected;
        private final T candidate;
        private boolean consumed;
        private Prepared(Revision expected, T candidate) { this.expected = expected; this.candidate = candidate; }
        public Snapshot<T> apply(BooleanSupplier authorized) {
            ownerThread.run();
            if (consumed) throw new IllegalStateException("Settings proposal was already attempted");
            consumed = true;
            require(authorized);
            if (expected != revision) throw new IllegalStateException("Effective settings changed; read them again");
            if (!value.equals(candidate)) { value = candidate; revision = new Revision(); }
            return new Snapshot<>(revision, value);
        }
    }
    private final Runnable ownerThread;
    private Revision revision = new Revision();
    private T value;
    private boolean open = true;
    public SettingsPolicy(T initial, Runnable ownerThread) {
        value = Objects.requireNonNull(initial); this.ownerThread = Objects.requireNonNull(ownerThread);
    }
    private void require(BooleanSupplier authorized) {
        ownerThread.run();
        if (!open) throw new IllegalStateException("Settings context has stopped");
        if (!authorized.getAsBoolean()) throw new SecurityException("Operator permission is required");
    }
    public Snapshot<T> read(BooleanSupplier authorized) { require(authorized); return new Snapshot<>(revision, value); }
    /** Internal consumer read; administrative entry points must call read with actual authorization. */
    public T current() { ownerThread.run(); if (!open) throw new IllegalStateException("Settings context has stopped"); return value; }
    public Prepared prepare(Revision expected, T candidate, BooleanSupplier authorized) {
        require(authorized);
        if (expected != revision) throw new IllegalStateException("Effective settings changed; read them again");
        return new Prepared(expected, Objects.requireNonNull(candidate));
    }
    public void close() { ownerThread.run(); open = false; }
}
