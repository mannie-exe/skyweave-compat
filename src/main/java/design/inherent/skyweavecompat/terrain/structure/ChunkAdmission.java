package design.inherent.skyweavecompat.terrain.structure;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/** Owns queued registration, while leaving the engine's eventual future uncancelled. */
final class ChunkAdmission<T> implements Runnable {
    private final AtomicBoolean claimed = new AtomicBoolean();
    private final Supplier<CompletableFuture<T>> request;
    final CompletableFuture<T> result = new CompletableFuture<>();

    ChunkAdmission(Supplier<CompletableFuture<T>> request) { this.request = request; }

    @Override public void run() {
        if (!claimed.compareAndSet(false, true)) return;
        try {
            request.get().whenComplete((value, error) -> {
                if (error != null) result.completeExceptionally(error);
                else result.complete(value);
            });
        } catch (Throwable error) { result.completeExceptionally(error); }
    }

    /** Only for tasks returned unstarted by the owned executor's shutdownNow. */
    void discard() {
        if (claimed.compareAndSet(false, true))
            result.completeExceptionally(new CancellationException("Structure admission retired before registration"));
    }
}
