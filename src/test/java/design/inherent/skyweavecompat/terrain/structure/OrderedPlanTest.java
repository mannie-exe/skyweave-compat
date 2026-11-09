package design.inherent.skyweavecompat.terrain.structure;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OrderedPlanTest {
    @Test @org.junit.jupiter.api.Timeout(5)
    void peekingDoesNotWaitForOrPublishAnInProgressStep() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var plan = new OrderedPlan<Integer>(2, 0, (index, previous) -> {
            if (index == 1) {
                entered.countDown();
                try { if (!release.await(3, TimeUnit.SECONDS)) throw new AssertionError("Peek blocked on fitting"); }
                catch (InterruptedException failure) { throw new AssertionError(failure); }
            }
            return previous + index + 1;
        });
        assertNull(plan.peek(0)); assertEquals(1, plan.through(0));
        var fitting = CompletableFuture.supplyAsync(() -> plan.through(1));
        assertTrue(entered.await(1, TimeUnit.SECONDS));
        try { assertEquals(1, plan.peek(0)); assertNull(plan.peek(1)); }
        finally { release.countDown(); }
        assertEquals(3, fitting.join()); assertEquals(3, plan.peek(1));
    }
    @Test void laterFamiliesAreDeferredAndPrefixesAreImmutable() {
        var calls = new ArrayList<Integer>();
        var plan = new OrderedPlan<List<Integer>>(13, List.of(), (index, previous) -> {
            calls.add(index);
            var next = new ArrayList<>(previous); next.add(index); return List.copyOf(next);
        });
        var villages = plan.through(1);
        assertEquals(List.of(0, 1), villages);
        assertEquals(List.of(0, 1), calls);
        assertEquals(villages, plan.through(0));
        var full = plan.through(12);
        assertEquals(java.util.stream.IntStream.range(0, 13).boxed().toList(), full);
        assertEquals(full, calls);
        assertEquals(List.of(0, 1), villages);
    }

    @Test void concurrentRequestsUseTheSameOrderedWorkAndMatchEagerEvaluation() {
        var calls = new AtomicInteger();
        var plan = new OrderedPlan<Long>(13, 17L, (index, previous) -> {
            calls.incrementAndGet(); return previous * 31 + index;
        });
        var requests = java.util.stream.IntStream.range(0, 40).mapToObj(index ->
                CompletableFuture.supplyAsync(() -> plan.through(index % 13))).toList();
        requests.forEach(CompletableFuture::join);
        long eager = 17;
        for (int index = 0; index < 13; index++) eager = eager * 31 + index;
        assertEquals(eager, plan.through(12));
        assertEquals(13, calls.get());
    }

    @Test void failedStepsDoNotPublishPartialStateAndCanBeRetried() {
        var calls = new AtomicInteger();
        var plan = new OrderedPlan<Integer>(3, 0, (index, previous) -> {
            if (index == 1 && calls.getAndIncrement() == 0) throw new IllegalStateException("failed fit");
            return previous + index + 1;
        });
        assertThrows(IllegalStateException.class, () -> plan.through(2));
        assertEquals(1, plan.through(0));
        assertEquals(6, plan.through(2));
        assertThrows(IllegalArgumentException.class, () -> plan.through(3));
    }
}
