package design.inherent.skyweavecompat.dh;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CompletionAwaitTest {
    @Test void levelJoinCompletesTheOwnerDependencyAndPreservesTheActualResult() throws Exception {
        var executor=Executors.newSingleThreadExecutor();
        var upload=new CompletableFuture<Object>(); var closed=new CompletableFuture<Object>();
        var started=new CountDownLatch(1); Object value=new Object();
        executor.execute(() -> { started.countDown(); closed.complete(upload.join()); });
        assertTrue(started.await(1,TimeUnit.SECONDS)); var services=new AtomicInteger();
        try {
            Object result=CompletionAwait.join(closed,() -> true,() -> {
                services.incrementAndGet(); upload.complete(value);
            },closed::join);
            assertSame(value,result); assertTrue(services.get()>0);
            assertFalse(closed.isCancelled()); assertFalse(upload.isCancelled());
        } finally { upload.complete(value); executor.shutdown(); assertTrue(executor.awaitTermination(1,TimeUnit.SECONDS)); }
    }

    @Test void terminalJoinsNeverProbeOrServiceAnOwnerAndPreserveNativeFailures() {
        var cause=new IllegalStateException("actual close failure");
        for (var future:java.util.List.of(CompletableFuture.completedFuture("value"),CompletableFuture.<String>failedFuture(cause),new CompletableFuture<String>())) {
            if (!future.isDone()) future.cancel(false);
            var calls=new AtomicInteger();
            java.util.function.Supplier<String> original=() -> { calls.incrementAndGet(); return future.join(); };
            if (future.isCancelled()) assertThrows(java.util.concurrent.CancellationException.class,() -> CompletionAwait.join(future,() -> { fail("terminal owner probe"); return false; },() -> fail("terminal service"),original));
            else if (future.isCompletedExceptionally()) {
                var failure=assertThrows(java.util.concurrent.CompletionException.class,() -> CompletionAwait.join(future,() -> { fail("terminal owner probe"); return false; },() -> fail("terminal service"),original));
                assertSame(cause,failure.getCause());
            } else assertEquals("value",CompletionAwait.join(future,() -> { fail("terminal owner probe"); return false; },() -> fail("terminal service"),original));
            assertEquals(1,calls.get());
        }
    }

    @Test void lostJoinOwnerDelegatesWithoutMoreServiceOrCancellingItsFuture() {
        var future=new CompletableFuture<String>(); var services=new AtomicInteger(); var originals=new AtomicInteger();
        String result=CompletionAwait.join(future,() -> services.get()==0,services::incrementAndGet,() -> {
            originals.incrementAndGet(); future.complete("native result"); return future.join();
        });
        assertEquals("native result",result); assertEquals(1,services.get()); assertEquals(1,originals.get()); assertFalse(future.isCancelled());
    }

    @Test void interruptedJoinRetainsNativeUninterruptibleResultAndInterruptFlag() throws Exception {
        var future=new CompletableFuture<String>(); var enteredOriginal=new CountDownLatch(1); var finished=new CompletableFuture<Boolean>();
        Thread thread=new Thread(() -> {
            Thread.currentThread().interrupt();
            try {
                String result=CompletionAwait.join(future,() -> { fail("interrupted owner probe"); return true; },() -> fail("interrupted service"),() -> {
                    enteredOriginal.countDown(); return future.join();
                });
                finished.complete(result.equals("native result") && Thread.currentThread().isInterrupted());
            } catch (Throwable failure) { finished.completeExceptionally(failure); }
        });
        thread.start();
        try {
            assertTrue(enteredOriginal.await(1,TimeUnit.SECONDS)); assertFalse(finished.isDone()); future.complete("native result");
            assertTrue(finished.get(1,TimeUnit.SECONDS)); assertFalse(future.isCancelled());
        } finally { future.complete("cleanup"); thread.join(1000); assertFalse(thread.isAlive()); }
    }

    @Test void interruptionDuringJoinSliceFallsBackWithoutMoreOwnerService() throws Exception {
        var future=new CompletableFuture<String>(); var serviced=new CountDownLatch(1); var originalEntered=new CountDownLatch(1);
        var services=new AtomicInteger(); var finished=new CompletableFuture<Boolean>();
        Thread thread=new Thread(() -> {
            try {
                String result=CompletionAwait.join(future,() -> true,() -> { services.incrementAndGet(); serviced.countDown(); },() -> {
                    originalEntered.countDown(); return future.join();
                });
                finished.complete(result.equals("native result") && Thread.currentThread().isInterrupted());
            } catch (Throwable failure) { finished.completeExceptionally(failure); }
        });
        thread.start();
        try {
            assertTrue(serviced.await(1,TimeUnit.SECONDS)); thread.interrupt();
            assertTrue(originalEntered.await(1,TimeUnit.SECONDS)); int before=services.get();
            assertFalse(finished.isDone()); future.complete("native result");
            assertTrue(finished.get(1,TimeUnit.SECONDS)); assertEquals(before,services.get()); assertFalse(future.isCancelled());
        } finally { future.complete("cleanup"); thread.join(1000); assertFalse(thread.isAlive()); }
    }

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
