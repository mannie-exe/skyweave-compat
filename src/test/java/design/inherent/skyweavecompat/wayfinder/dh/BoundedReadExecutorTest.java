package design.inherent.skyweavecompat.wayfinder.dh;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class BoundedReadExecutorTest {
    @Test void acceptedReadStillDisposesAfterRecipientClosesBeforeWorkerStarts() throws Exception {
        var start = new CountDownLatch(1);
        var gate = new BoundedReadExecutor(task -> daemon(() -> { await(start); task.run(); }));
        var closed = new AtomicBoolean();
        var reads = new AtomicInteger();
        var disposals = new AtomicInteger();
        var delivered = new CompletableFuture<Boolean>();
        try {
            assertTrue(gate.submit(reads::incrementAndGet, (value, failure) -> {
                if (closed.get()) disposals.incrementAndGet();
                delivered.complete(failure == null && value == 1);
            }));
            assertFalse(gate.submit(() -> 2, (value, failure) -> fail("Second read admitted")));
            assertEquals(0, reads.get());
            closed.set(true);
        } finally { start.countDown(); }
        assertTrue(delivered.get(5, TimeUnit.SECONDS));
        assertEquals(1, disposals.get());
        assertReleased(gate);
    }

    @Test void runningReadAndLateDisposalRetainTheSingleSlot() throws Exception {
        var entered = new CountDownLatch(1);
        var finishRead = new CountDownLatch(1);
        var disposing = new CountDownLatch(1);
        var finishDisposal = new CountDownLatch(1);
        var gate = new BoundedReadExecutor(BoundedReadExecutorTest::daemon);
        var delivered = new CompletableFuture<Integer>();
        try {
            assertTrue(gate.submit(() -> { entered.countDown(); await(finishRead); return 7; }, (value, failure) -> {
                disposing.countDown(); await(finishDisposal); delivered.complete(value);
            }));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertFalse(gate.submit(() -> 2, (value, failure) -> fail("Overlapping running read")));
            finishRead.countDown();
            assertTrue(disposing.await(5, TimeUnit.SECONDS));
            assertFalse(gate.submit(() -> 2, (value, failure) -> fail("Read admitted before pooled disposal")));
        } finally { finishRead.countDown(); finishDisposal.countDown(); }
        assertEquals(7, delivered.get(5, TimeUnit.SECONDS));
        assertReleased(gate);
    }

    @Test void readAndDeliveryFailuresReleaseTheSlot() throws Exception {
        var uncaught = new CompletableFuture<Throwable>();
        var gate = new BoundedReadExecutor(task -> {
            var thread = daemon(task);
            thread.setUncaughtExceptionHandler((owner, failure) -> uncaught.complete(failure));
            return thread;
        });
        var readFailure = new IllegalStateException("stored data unavailable");
        var deliveryFailure = new IllegalStateException("recipient closed");
        var delivered = new CompletableFuture<Throwable>();
        assertTrue(gate.submit(() -> { throw readFailure; }, (value, failure) -> {
            delivered.complete(failure);
            throw deliveryFailure;
        }));
        assertSame(readFailure, delivered.get(5, TimeUnit.SECONDS));
        assertSame(deliveryFailure, uncaught.get(5, TimeUnit.SECONDS));
        assertReleased(gate);
    }

    private static void assertReleased(BoundedReadExecutor gate) throws Exception {
        var next = new CompletableFuture<Integer>();
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            while (!gate.submit(() -> 9, (value, failure) -> next.complete(value))) Thread.onSpinWait();
        });
        assertEquals(9, next.get(5, TimeUnit.SECONDS));
    }

    private static Thread daemon(Runnable task) {
        var thread = new Thread(task, "bounded-read-test"); thread.setDaemon(true); return thread;
    }
    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("Read fixture timed out");
        } catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new AssertionError(error); }
    }
}
