package design.inherent.skyweavecompat.dh;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Service admitted owner dependencies while preserving native completion waits. */
public final class CompletionAwait {
    private static final long SLICE_NANOS = TimeUnit.MILLISECONDS.toNanos(5);
    private CompletionAwait() {}

    @FunctionalInterface
    public interface NativeAwait {
        boolean await(long amount, TimeUnit unit) throws InterruptedException;
    }

    /** Preserve native join's result and uninterruptible fallback; service only its owner dependency. */
    public static <T> T join(CompletableFuture<T> future, BooleanSupplier ownerReady,
            Runnable serviceOwner, Supplier<T> original) {
        while (!future.isDone()) {
            if (Thread.currentThread().isInterrupted() || !ownerReady.getAsBoolean()) return original.get();
            serviceOwner.run();
            if (future.isDone()) break;
            try { future.get(SLICE_NANOS, TimeUnit.NANOSECONDS); }
            catch (TimeoutException ignored) { }
            catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt(); return original.get();
            } catch (ExecutionException | java.util.concurrent.CancellationException terminal) {
                break; // The original join owns exception wrapping and cancellation semantics.
            }
        }
        return original.get();
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
