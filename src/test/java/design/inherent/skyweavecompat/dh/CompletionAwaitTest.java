package design.inherent.skyweavecompat.dh;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CompletionAwaitTest {
    @Test void realWorkerCompletesItsDependencyWithoutInterruptingUsefulWork() throws Exception {
        var executor = Executors.newSingleThreadExecutor();
        var dependency = new CompletableFuture<String>();
        var started = new CountDownLatch(1);
        var result = new CompletableFuture<String>();
        executor.execute(() -> { started.countDown(); result.complete(dependency.join()); });
        assertTrue(started.await(1, TimeUnit.SECONDS)); executor.shutdown();
        try {
            assertFalse(executor.awaitTermination(10, TimeUnit.MILLISECONDS), "native wait cannot finish before its owner dependency");
            var calls = new AtomicInteger();
            assertTrue(CompletionAwait.await(executor, 1, TimeUnit.SECONDS, () -> true,
                    () -> { calls.incrementAndGet(); dependency.complete("useful result"); }, executor::awaitTermination));
            assertEquals("useful result", result.join()); assertEquals(1, calls.get());
        } finally { dependency.complete("cleanup"); executor.shutdownNow(); executor.awaitTermination(1, TimeUnit.SECONDS); }
    }

    @Test void terminatedWaitNeverServicesOrProbesAnotherOwner() throws Exception {
        var executor = Executors.newSingleThreadExecutor(); executor.shutdown();
        assertTrue(executor.awaitTermination(1, TimeUnit.SECONDS));
        var calls = new AtomicInteger();
        assertTrue(CompletionAwait.await(executor, 5, TimeUnit.SECONDS,
                () -> { fail("terminal executor must not inspect a renderer"); return true; },
                () -> fail("no post-termination callbacks"), (amount, unit) -> {
                    assertEquals(5, amount); assertEquals(TimeUnit.SECONDS, unit); calls.incrementAndGet();
                    return executor.awaitTermination(amount, unit);
                }));
        assertEquals(1, calls.get());
    }

    @Test void nonpositiveAndWrongOwnerWaitsPreserveTheOriginalCall() throws Exception {
        var executor = Executors.newSingleThreadExecutor();
        try {
            for (long amount : new long[]{0, -1}) {
                var calls = new AtomicInteger();
                assertFalse(CompletionAwait.await(executor, amount, TimeUnit.MILLISECONDS,
                        () -> { fail("nonpositive wait must not inspect owner"); return true; },
                        () -> fail("no callbacks"), (value, unit) -> {
                            assertEquals(amount, value); assertEquals(TimeUnit.MILLISECONDS, unit);
                            calls.incrementAndGet(); return executor.awaitTermination(value, unit);
                        }));
                assertEquals(1, calls.get());
            }
            var calls = new AtomicInteger();
            assertFalse(CompletionAwait.await(executor, 1, TimeUnit.MILLISECONDS, () -> false,
                    () -> fail("foreign owner must not service callbacks"), (amount, unit) -> {
                        assertEquals(1, amount); assertEquals(TimeUnit.MILLISECONDS, unit);
                        calls.incrementAndGet(); return executor.awaitTermination(amount, unit);
                    }));
            assertEquals(1, calls.get());
        } finally { executor.shutdownNow(); executor.awaitTermination(1, TimeUnit.SECONDS); }
    }

    @Test void lostContextDelegatesOnlyRemainingBudgetWithoutMoreCallbacks() throws Exception {
        var executor = Executors.newSingleThreadExecutor(); var release = new CountDownLatch(1); var started = new CountDownLatch(1);
        executor.execute(() -> { started.countDown(); try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } });
        assertTrue(started.await(1, TimeUnit.SECONDS)); executor.shutdown();
        try {
            var services = new AtomicInteger(); var waits = new AtomicInteger();
            assertFalse(CompletionAwait.await(executor, 30, TimeUnit.MILLISECONDS, () -> services.get() == 0,
                    services::incrementAndGet, (amount, unit) -> {
                        assertTrue(amount >= 0 && unit.toNanos(amount) <= TimeUnit.MILLISECONDS.toNanos(30));
                        waits.incrementAndGet(); return executor.awaitTermination(amount, unit);
                    }));
            assertEquals(1, services.get()); assertTrue(waits.get() >= 1);
        } finally { release.countDown(); executor.shutdownNow(); executor.awaitTermination(1, TimeUnit.SECONDS); }
    }

    @Test void nativeInterruptedWaitPropagatesWithoutServicingCallbacks() throws Exception {
        var executor = Executors.newSingleThreadExecutor(); var release = new CountDownLatch(1); var started = new CountDownLatch(1);
        executor.execute(() -> { started.countDown(); try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } });
        assertTrue(started.await(1, TimeUnit.SECONDS)); executor.shutdown();
        try {
            Thread.currentThread().interrupt();
            assertThrows(InterruptedException.class, () -> CompletionAwait.await(executor, 1, TimeUnit.SECONDS,
                    () -> true, () -> fail("do not pump after interruption"), executor::awaitTermination));
        } finally { Thread.interrupted(); release.countDown(); executor.shutdownNow(); executor.awaitTermination(1, TimeUnit.SECONDS); }
    }
}
