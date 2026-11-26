package design.inherent.skyweavecompat.dh;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

/** Worker-local retry state. Native queues remain the only owners of updates. */
public final class UpdateQueueWait {
    private static final long MAX_RETRY_NANOS = TimeUnit.MILLISECONDS.toNanos(50);
    private final AtomicLong epoch = new AtomicLong();
    private volatile Thread waiter;
    private volatile Thread worker;
    private volatile boolean closed;
    // The following fields belong exclusively to the native queueing worker.
    private long passEpoch;
    private long retryNanos;
    private boolean dispatched;

    public void beginPass() {
        worker = Thread.currentThread();
        passEpoch = epoch.get();
        retryNanos = 0;
        dispatched = false;
    }

    public void retried(long elapsedNanos, long debounceNanos) {
        if (Thread.currentThread() != worker) return;
        long delay = elapsedNanos >= 0 && elapsedNanos < debounceNanos
                ? Math.min(MAX_RETRY_NANOS, debounceNanos - elapsedNanos) : MAX_RETRY_NANOS;
        retryNanos = retryNanos == 0 ? delay : Math.min(retryNanos, delay);
    }

    public void dispatched() {
        if (Thread.currentThread() == worker) dispatched = true;
    }

    public void awaitRetry() {
        if (dispatched || retryNanos <= 0) return;
        Thread current = Thread.currentThread();
        waiter = current;
        try {
            // Enqueue before this check changes the epoch; enqueue after it leaves an unpark permit.
            if (!closed && !current.isInterrupted() && epoch.get() == passEpoch)
                LockSupport.parkNanos(this, retryNanos);
        } finally {
            waiter = null;
        }
    }

    public void signal() {
        // Native client passes call setCenter themselves; that is not a new external update.
        if (Thread.currentThread() == worker) return;
        epoch.incrementAndGet();
        Thread current = waiter;
        if (current != null) LockSupport.unpark(current);
    }

    public void close() {
        closed = true;
        signal();
    }
}
