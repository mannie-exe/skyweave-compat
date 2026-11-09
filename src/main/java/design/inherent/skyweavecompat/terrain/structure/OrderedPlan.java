package design.inherent.skyweavecompat.terrain.structure;

import java.util.Objects;
import java.util.function.BiFunction;

/** Publish immutable family prefixes only after each complete step succeeds. */
final class OrderedPlan<T> {
    private final int size;
    private final BiFunction<Integer, T, T> step;
    private volatile T value;
    private volatile int completed;
    private boolean extending;

    OrderedPlan(int size, T initial, BiFunction<Integer, T, T> step) {
        if (size < 1) throw new IllegalArgumentException("Empty plan order");
        this.size = size;
        this.value = Objects.requireNonNull(initial);
        this.step = Objects.requireNonNull(step);
    }

    /** A published immutable prefix, without waiting for an in-progress fit or starting work. */
    T peek(int index) {
        if (index < 0 || index >= size) throw new IllegalArgumentException("Invalid plan prefix");
        return completed > index ? value : null;
    }

    synchronized T through(int index) {
        if (index < 0 || index >= size) throw new IllegalArgumentException("Invalid plan prefix");
        if (extending) throw new IllegalStateException("Recursive structure planning");
        extending = true;
        try {
            while (completed <= index) {
                T next = Objects.requireNonNull(step.apply(completed, value));
                value = next;
                completed++;
            }
            return value;
        } finally { extending = false; }
    }
}
