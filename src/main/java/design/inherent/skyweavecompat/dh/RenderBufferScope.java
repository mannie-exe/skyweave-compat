/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.dh;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

/** Native render-worker loans, returned once only after their actual upload usage settles. */
public final class RenderBufferScope implements AutoCloseable {
    private static final ThreadLocal<RenderBufferScope> WORKER = new ThreadLocal<>();
    private static final ThreadLocal<RenderBufferScope> RETURN = new ThreadLocal<>();
    private enum State { BORROWED, RETURNING, RETURNED, FAILED }
    private static final class Loan {
        final Object identity;
        final Runnable nativeClose;
        State state = State.BORROWED;
        Loan(Object identity, Runnable nativeClose) { this.identity = identity; this.nativeClose = nativeClose; }
    }
    private final ArrayList<Loan> loans = new ArrayList<>(4);
    private RenderBufferScope previous;
    private CompletableFuture<?> upload;
    private boolean submitted, closed;

    private RenderBufferScope() { previous = WORKER.get(); WORKER.set(this); }
    public static RenderBufferScope open() { return new RenderBufferScope(); }
    public static RenderBufferScope currentWorker() { return WORKER.get(); }

    public synchronized void borrowed(Object identity, Runnable nativeClose) {
        if (closed || identity == null || nativeClose == null) throw new IllegalStateException("Invalid native render-buffer loan");
        for (var loan : loans) if (loan.identity == identity) throw new IllegalStateException("Repeated render-buffer loan in one worker");
        loans.add(new Loan(identity, nativeClose));
    }
    public synchronized void submissionStarted() {
        if (closed) throw new IllegalStateException("Native submission after worker retirement");
        submitted = true;
    }
    public synchronized void bindUpload(CompletableFuture<?> future) {
        if (closed || future == null || (upload != null && upload != future)) throw new IllegalStateException("Inconsistent native upload owner");
        upload = future;
    }

    public static void nativeReturn(Object identity, Runnable original) {
        var scope = RETURN.get(); if (scope == null) scope = WORKER.get();
        if (scope == null) original.run(); else scope.returnOnce(identity, original);
    }
    private void returnOnce(Object identity, Runnable original) {
        Loan selected = null;
        synchronized (this) {
            for (var loan : loans) if (loan.identity == identity) { selected = loan; break; }
            if (selected != null) {
                if (selected.state != State.BORROWED) return;
                selected.state = State.RETURNING;
            }
        }
        if (selected == null) { original.run(); return; }
        boolean returned = false;
        try { original.run(); returned = true; }
        finally { synchronized (this) { selected.state = returned ? State.RETURNED : State.FAILED; } }
    }

    /** Capture this generation, so an old native callback cannot return a newly reused object. */
    public Runnable nativeReturnCallback(Runnable original) { return () -> withReturnOwner(original); }
    private void withReturnOwner(Runnable operation) {
        var previousReturn = RETURN.get(); RETURN.set(this);
        try { operation.run(); }
        finally { if (previousReturn == null) RETURN.remove(); else RETURN.set(previousReturn); }
    }
    private void returnOutstanding() {
        withReturnOwner(() -> {
            Throwable failure = null;
            for (var loan : loans) {
                boolean pending;
                synchronized (this) { pending = loan.state == State.BORROWED; }
                if (!pending) continue;
                try { loan.nativeClose.run(); }
                catch (RuntimeException | Error error) {
                    if (failure == null) failure = error; else if (failure != error) failure.addSuppressed(error);
                }
            }
            if (failure instanceof Error error) throw error;
            if (failure instanceof RuntimeException error) throw error;
        });
    }

    @Override public void close() {
        CompletableFuture<?> future; boolean submission;
        synchronized (this) {
            if (closed) return;
            if (WORKER.get() != this) throw new IllegalStateException("Native render-worker scope closed out of order");
            closed = true; future = upload; submission = submitted;
        }
        try {
            if (!submission) returnOutstanding();
            else if (future != null) {
                // This is the private, unexposed native upload result: normal/exceptional
                // completion follows actual upload work. Cancellation alone does not prove it.
                if (future.isDone()) { if (!future.isCancelled()) returnOutstanding(); }
                else future.whenComplete((value, error) -> { if (!future.isCancelled()) returnOutstanding(); });
            }
            // Unknown partial submission or an independently cancelled result is not a
            // license to recycle bytes. Preserve native ownership instead of freeing early.
        } finally {
            if (previous == null) WORKER.remove(); else WORKER.set(previous);
            previous = null;
        }
    }
}
