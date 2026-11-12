package design.inherent.skyweavecompat.wayfinder.dh;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/** One actual read, including delivery/disposal. Foreign executor retirement cannot strand it. */
final class BoundedReadExecutor {
    private final AtomicBoolean busy = new AtomicBoolean();
    private final ThreadPoolExecutor worker;

    BoundedReadExecutor() {
        this(task -> {
            var thread = new Thread(task, "Skyweave Wayfinder cache reader");
            thread.setDaemon(true);
            return thread;
        });
    }

    BoundedReadExecutor(ThreadFactory threads) {
        // The queue covers the brief interval between releasing a completed job's gate
        // and its worker returning to the executor. The gate admits no queued read tail.
        worker = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1), threads);
        worker.allowCoreThreadTimeOut(true);
    }

    <T> boolean submit(Supplier<T> read, BiConsumer<T, Throwable> deliver) {
        if (!busy.compareAndSet(false, true)) return false;
        try {
            worker.execute(() -> {
                T result = null;
                Throwable failure = null;
                try { result = read.get(); }
                catch (Throwable error) { failure = error; }
                try { deliver.accept(result, failure); }
                finally { busy.set(false); }
            });
        } catch (RuntimeException rejected) {
            busy.set(false);
            throw rejected;
        }
        return true;
    }
}
