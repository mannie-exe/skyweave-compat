package design.inherent.skyweavecompat.rendering;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CoverageRequestsTest {
    private static CoverageRequests.Bounds bounds(int distance) { return new CoverageRequests.Bounds(0, 0, distance, 0, distance, 0, 60, 80); }
    @Test void noPartialOrPreviousFrameCreditAndChangedOwnershipDropsAllAnswers() {
        var queue = new CoverageRequests(4, 64, 128); var owner = new Object();
        var missing = new AtomicInteger(1);
        CoverageRequests.Probe probe = (cell, bounds) -> cell.x() == missing.get() ? CoverageColumns.State.UNKNOWN : CoverageColumns.State.EMPTY;
        queue.begin(1, owner); assertFalse(queue.ready("ship", bounds(4), probe));
        missing.set(-100); assertFalse(queue.ready("ship", bounds(4), probe));
        queue.begin(2, owner); assertTrue(queue.ready("ship", bounds(4), probe));
        missing.set(1); queue.begin(3, owner); assertFalse(queue.ready("ship", bounds(4), probe));
        queue.begin(4, new Object()); assertEquals(0, queue.size());
        assertFalse(queue.ready("ship", bounds(4), probe));
    }

    @Test void boundedQueueRotatesDeferredWorkAndEvictsWithoutVisibleFallback() {
        var queue = new CoverageRequests(2, 16, 16); var owner = new Object();
        CoverageRequests.Probe probe = (cell, bounds) -> CoverageColumns.State.READY;
        queue.begin(1, owner);
        assertTrue(queue.ready("a", bounds(4), probe));
        assertFalse(queue.ready("b", bounds(4), probe));
        assertTrue(queue.reads() <= 16);
        queue.begin(2, owner);
        assertTrue(queue.ready("b", bounds(4), probe));
        assertTrue(queue.reads() <= 16);
        queue.ready("c", bounds(1000), probe); assertEquals(2, queue.size());
        queue.begin(3, owner); assertFalse(queue.ready("c", bounds(1000), probe));
        queue.clear(); assertEquals(0, queue.size());
    }
}
