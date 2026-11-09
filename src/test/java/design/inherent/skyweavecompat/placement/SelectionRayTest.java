package design.inherent.skyweavecompat.placement;

import design.inherent.skyweavecompat.macaw.SelectionRay;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SelectionRayTest {
    @Test void rejectsForeignFramesAndOffSegmentHits() {
        assertFalse(SelectionRay.contains(6, 0, 0, 30_000_000, 0, 0));
        assertFalse(SelectionRay.contains(6, 0, 0, -1, 0, 0));
        assertFalse(SelectionRay.contains(6, 0, 0, 7, 0, 0));
        assertFalse(SelectionRay.contains(6, 0, 0, 3, 1, 0));
        assertFalse(SelectionRay.contains(6, 0, 0, Double.NaN, 0, 0));
        assertFalse(SelectionRay.contains(Double.POSITIVE_INFINITY, 0, 0, 3, 0, 0));
        assertFalse(SelectionRay.contains(0, 0, 0, 0, 0, 0));
    }

    @Test void preservesLongAndBoundarySegmentsWithoutAPlayerReachLimit() {
        assertTrue(SelectionRay.contains(65536, 1024, -65536, 32768, 512, -32768));
        assertTrue(SelectionRay.contains(6, 0, 0, 0, 0, 0));
        assertTrue(SelectionRay.contains(6, 0, 0, 6, 0, 0));
        assertTrue(SelectionRay.contains(6, 0, 0, 3, 1e-8, 0));
        assertEquals(9, SelectionRay.cellBudget(-1.5, 0, 0, 3.5, 0, 0));
        assertEquals(12, SelectionRay.cellBudget(30_000_000.5, 20, 0, 30_000_008.5, 20, 0));
        assertEquals(0, SelectionRay.cellBudget(0, 0, 0, Double.NaN, 0, 0));
    }
}
