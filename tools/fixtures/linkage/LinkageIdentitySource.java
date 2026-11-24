package linearreview;

import java.util.UUID;
import java.util.function.Supplier;

/** Fixture-only identity input, consumed before native allocation publishes a body. */
public final class LinkageIdentitySource {
    private static final ThreadLocal<UUID> requested = new ThreadLocal<>();
    private static final ThreadLocal<Integer> consumed = ThreadLocal.withInitial(() -> 0);

    public static <T> T allocate(long id, Supplier<T> assembly) {
        if (requested.get() != null) throw new AssertionError("nested fixture identity input");
        requested.set(new UUID(0, id));
        consumed.set(0);
        try {
            T value = assembly.get();
            if (consumed.get() != 1) throw new AssertionError("identity allocation calls: " + consumed.get());
            return value;
        } finally {
            requested.remove();
            consumed.remove();
        }
    }

    public static UUID next(Supplier<UUID> nativeSource) {
        UUID input = requested.get();
        if (input == null) return nativeSource.get();
        consumed.set(consumed.get() + 1);
        return input;
    }
}
