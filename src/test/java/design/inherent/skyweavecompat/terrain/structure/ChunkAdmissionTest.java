package design.inherent.skyweavecompat.terrain.structure;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChunkAdmissionTest {
    @Test void shutdownSettlesUnstartedRegistrationsWithoutCallingEngine() throws Exception {
        var started = new CountDownLatch(1);
        var executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(2));
        try {
            executor.execute(() -> {
                started.countDown();
                try { new CountDownLatch(1).await(); }
                catch (InterruptedException expected) { Thread.currentThread().interrupt(); }
            });
            assertTrue(started.await(2, TimeUnit.SECONDS));
            var calls = new AtomicInteger();
            var first = new ChunkAdmission<>(() -> { calls.incrementAndGet(); return CompletableFuture.completedFuture(1); });
            var second = new ChunkAdmission<>(() -> { calls.incrementAndGet(); return CompletableFuture.completedFuture(2); });
            executor.execute(first); executor.execute(second);
            var queued = executor.shutdownNow(); assertEquals(2, queued.size());
            for (var task : queued) ((ChunkAdmission<?>)task).discard();
            assertThrows(CancellationException.class, first.result::join);
            assertThrows(CancellationException.class, second.result::join);
            first.run(); second.run(); assertEquals(0, calls.get());
            assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
        } finally { executor.shutdownNow(); }
    }

    @Test void lateEngineCompletionRetainsItsCustodyAndActualResult() {
        var nativeFuture = new CompletableFuture<String>();
        var request = new ChunkAdmission<>(() -> nativeFuture);
        request.run(); request.discard();
        assertFalse(nativeFuture.isCancelled()); assertFalse(request.result.isDone());
        nativeFuture.complete("actual"); assertEquals("actual", request.result.join());
    }

    @Test void engineSubmissionAndCompletionFailuresSettleOnce() {
        var failure = new IllegalStateException("native refusal");
        var request = new ChunkAdmission<String>(() -> { throw failure; });
        request.run(); request.run();
        assertSame(failure, assertThrows(CompletionException.class, request.result::join).getCause());
        var nativeFuture = new CompletableFuture<String>();
        var second = new ChunkAdmission<>(() -> nativeFuture);
        second.run(); nativeFuture.completeExceptionally(failure);
        assertSame(failure, assertThrows(CompletionException.class, second.result::join).getCause());
    }
}
