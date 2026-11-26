package design.inherent.skyweavecompat.dh;

import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UpdateQueueWaitTest {
    @Test void acceptedDispatchAndNoRetryDoNotThrottleUsefulPasses() {
        assertTimeoutPreemptively(Duration.ofSeconds(1), () -> {
            var wait = new UpdateQueueWait();
            for (int i = 0; i < 100; i++) {
                wait.beginPass(); wait.awaitRetry();
                wait.beginPass(); wait.retried(0, TimeUnit.SECONDS.toNanos(1));
                wait.dispatched(); wait.awaitRetry();
            }
        });
    }

    @Test void notificationBeforeWaitRegistrationCannotBeLost() throws Exception {
        var wait = new UpdateQueueWait();
        var phases = new ArrayBlockingQueue<CountDownLatch>(1);
        var worker = new Thread(() -> {
            try {
                for (int i = 0; i < 100; i++) {
                    wait.beginPass(); wait.retried(0, TimeUnit.SECONDS.toNanos(1));
                    var proceed = new CountDownLatch(1);
                    phases.put(proceed); proceed.await();
                    wait.awaitRetry();
                }
            } catch (InterruptedException failure) { Thread.currentThread().interrupt(); }
        });
        worker.start();
        try {
            // A lost notification would impose 100 native 50ms retry waits.
            assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
                for (int i = 0; i < 100; i++) {
                    var proceed = phases.poll(1, TimeUnit.SECONDS);
                    assertNotNull(proceed);
                    wait.signal(); proceed.countDown();
                }
                worker.join(1000);
                assertFalse(worker.isAlive());
            });
        } finally { worker.interrupt(); worker.join(1000); }
    }

    @Test void interruptedOrRetiredWorkersKeepTheirTerminalState() {
        var wait = new UpdateQueueWait();
        Thread.currentThread().interrupt();
        try {
            wait.beginPass(); wait.retried(0, TimeUnit.SECONDS.toNanos(1)); wait.awaitRetry();
            assertTrue(Thread.currentThread().isInterrupted(), "waiting must not consume the native close interrupt");
        } finally { Thread.interrupted(); }
        wait.close();
        assertTimeoutPreemptively(Duration.ofSeconds(1), () -> {
            for (int i = 0; i < 100; i++) {
                wait.beginPass(); wait.retried(0, TimeUnit.SECONDS.toNanos(1)); wait.awaitRetry();
            }
        });
    }
}
