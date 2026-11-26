/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.dh;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RenderBufferScopeTest {
    private static void borrow(RenderBufferScope scope, Object identity, AtomicInteger returns) {
        scope.borrowed(identity, () -> RenderBufferScope.nativeReturn(identity, returns::incrementAndGet));
    }
    @Test void nativeSuccessfulReturnsAreNotRepeatedByCleanup() {
        var count = new AtomicInteger(); Object lease = new Object();
        try (var scope = RenderBufferScope.open()) {
            borrow(scope, lease, count);
            RenderBufferScope.nativeReturn(lease, count::incrementAndGet);
        }
        assertEquals(1, count.get()); assertNull(RenderBufferScope.currentWorker());
    }
    @Test void completedExceptionalUploadReturnsLoansAndPreservesItsFailure() {
        var count = new AtomicInteger(); var upload = new CompletableFuture<Void>();
        var failure = new IllegalStateException("actual native upload failure"); upload.completeExceptionally(failure);
        try (var scope = RenderBufferScope.open()) {
            borrow(scope, new Object(), count); borrow(scope, new Object(), count);
            scope.submissionStarted(); scope.bindUpload(upload);
        }
        assertEquals(2, count.get());
        assertSame(failure, assertThrows(CompletionException.class, upload::join).getCause());
    }
    @Test void unfinishedUploadKeepsBytesUntilActualCompletion() {
        var count = new AtomicInteger(); var upload = new CompletableFuture<Void>();
        try (var scope = RenderBufferScope.open()) {
            borrow(scope, new Object(), count); scope.submissionStarted(); scope.bindUpload(upload);
        }
        assertEquals(0, count.get()); assertFalse(upload.isDone());
        upload.complete(null); assertEquals(1, count.get()); assertNull(RenderBufferScope.currentWorker());
    }
    @Test void cancellationAloneDoesNotAuthorizeRecyclingOrCancelOtherWork() {
        var count = new AtomicInteger(); var upload = new CompletableFuture<Void>();
        try (var scope = RenderBufferScope.open()) {
            borrow(scope, new Object(), count); scope.submissionStarted(); scope.bindUpload(upload);
            upload.cancel(false);
        }
        assertEquals(0, count.get()); assertTrue(upload.isCancelled());
    }
    @Test void unknownPartialSubmissionCannotRecycleBytes() {
        var count = new AtomicInteger();
        try (var scope = RenderBufferScope.open()) { borrow(scope, new Object(), count); scope.submissionStarted(); }
        assertEquals(0, count.get()); assertNull(RenderBufferScope.currentWorker());
    }
    @Test void preSubmissionFailureReturnsLoansWithoutMaskingTheOriginalError() {
        var count = new AtomicInteger(); var failure = new IllegalStateException("native buffer construction");
        assertSame(failure, assertThrows(IllegalStateException.class, () -> {
            try (var scope = RenderBufferScope.open()) { borrow(scope, new Object(), count); throw failure; }
        }));
        assertEquals(1, count.get()); assertNull(RenderBufferScope.currentWorker());
    }
    @Test void lateCallbackCannotReturnAnObjectReusedByAnotherWorker() {
        Object reused = new Object(); var oldReturns = new AtomicInteger(); var newReturns = new AtomicInteger();
        Runnable late;
        try (var scope = RenderBufferScope.open()) {
            borrow(scope, reused, oldReturns);
            late = scope.nativeReturnCallback(() -> RenderBufferScope.nativeReturn(reused, oldReturns::incrementAndGet));
        }
        assertEquals(1, oldReturns.get());
        try (var scope = RenderBufferScope.open()) {
            borrow(scope, reused, newReturns); late.run();
            assertEquals(1, oldReturns.get()); assertEquals(0, newReturns.get());
        }
        assertEquals(1, newReturns.get());
    }
    @Test void identityRatherThanEqualsSeparatesNativeLoans() {
        class SameValue { @Override public boolean equals(Object ignored) { return true; } @Override public int hashCode() { return 1; } }
        var first = new AtomicInteger(); var second = new AtomicInteger();
        try (var scope = RenderBufferScope.open()) {
            borrow(scope, new SameValue(), first); borrow(scope, new SameValue(), second);
        }
        assertEquals(1, first.get()); assertEquals(1, second.get());
    }
    @Test void concurrentNativeCallbackAndWorkerCleanupReturnExactlyOnce() throws Exception {
        var count = new AtomicInteger(); var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        Object lease = new Object(); var scope = RenderBufferScope.open(); borrow(scope, lease, count);
        var callback = scope.nativeReturnCallback(() -> RenderBufferScope.nativeReturn(lease, () -> {
            entered.countDown();
            try { assertTrue(release.await(2, TimeUnit.SECONDS)); } catch (InterruptedException e) { throw new AssertionError(e); }
            count.incrementAndGet();
        }));
        var thread = new Thread(callback);
        try {
            thread.start(); assertTrue(entered.await(2, TimeUnit.SECONDS)); scope.close();
            assertEquals(0, count.get()); release.countDown(); thread.join(2000);
            assertFalse(thread.isAlive()); assertEquals(1, count.get());
        } finally { release.countDown(); thread.join(2000); scope.close(); }
    }
    @Test void nestedWorkersAndLateReturnContextRestoreTheirOwners() {
        try (var outer = RenderBufferScope.open()) {
            try (var inner = RenderBufferScope.open()) {
                assertThrows(IllegalStateException.class, outer::close); assertSame(inner, RenderBufferScope.currentWorker());
            }
            assertSame(outer, RenderBufferScope.currentWorker());
            var callback = outer.nativeReturnCallback(() -> { throw new IllegalStateException("native callback"); });
            assertThrows(IllegalStateException.class, callback::run); assertSame(outer, RenderBufferScope.currentWorker());
        }
        assertNull(RenderBufferScope.currentWorker());
    }
    @Test void failedPublicationIsNotRetriedAndOtherLoansStillReturn() {
        var attempts = new AtomicInteger(); var others = new AtomicInteger(); Object lease = new Object();
        var failure = new IllegalStateException("native pool return failed");
        try (var scope = RenderBufferScope.open()) {
            scope.borrowed(lease, () -> RenderBufferScope.nativeReturn(lease, () -> { attempts.incrementAndGet(); throw failure; }));
            borrow(scope, new Object(), others);
            assertSame(failure, assertThrows(IllegalStateException.class, () -> RenderBufferScope.nativeReturn(lease, () -> {
                attempts.incrementAndGet(); throw failure;
            })));
        }
        assertEquals(1, attempts.get()); assertEquals(1, others.get());
    }
    @Test void unownedReturnsKeepTheirOriginalBehavior() {
        var returns = new AtomicInteger();
        RenderBufferScope.nativeReturn(new Object(), returns::incrementAndGet);
        try (var scope = RenderBufferScope.open()) { RenderBufferScope.nativeReturn(new Object(), returns::incrementAndGet); }
        assertEquals(2, returns.get());
    }
    @Test void retiredScopesRejectNewLoansOrUploads() {
        var scope = RenderBufferScope.open(); scope.close();
        assertThrows(IllegalStateException.class, () -> scope.borrowed(new Object(), () -> {}));
        assertThrows(IllegalStateException.class, scope::submissionStarted);
        assertThrows(IllegalStateException.class, () -> scope.bindUpload(new CompletableFuture<>()));
    }
}
