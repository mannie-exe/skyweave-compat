package design.inherent.skyweavecompat.dh;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** Complete an original bounded wait while servicing its admitted owner dependency. */
public final class CompletionAwait {
    private static final long SLICE_NANOS = TimeUnit.MILLISECONDS.toNanos(5);
    private CompletionAwait() {}

    @FunctionalInterface
    public interface NativeAwait {
        boolean await(long amount, TimeUnit unit) throws InterruptedException;
    }

    public static boolean await(ExecutorService executor, long amount, TimeUnit unit,
            BooleanSupplier ownerReady, Runnable serviceOwner, NativeAwait original) throws InterruptedException {
        // Preserve native already-terminal, zero, negative and wrong-owner behavior without pumping.
        if (executor.isTerminated() || amount <= 0 || !ownerReady.getAsBoolean())
            return original.await(amount, unit);
        long budget = unit.toNanos(amount);
        long started = System.nanoTime();
        while (true) {
            if (executor.isTerminated()) return true;
            long remaining = budget - (System.nanoTime() - started);
            if (remaining <= 0) return original.await(0, TimeUnit.NANOSECONDS);
            if (Thread.currentThread().isInterrupted() || !ownerReady.getAsBoolean())
                return original.await(remaining, TimeUnit.NANOSECONDS);
            serviceOwner.run();
            if (executor.isTerminated()) return true;
            remaining = budget - (System.nanoTime() - started);
            if (remaining <= 0) return original.await(0, TimeUnit.NANOSECONDS);
            if (original.await(Math.min(SLICE_NANOS, remaining), TimeUnit.NANOSECONDS)) return true;
        }
    }
}
