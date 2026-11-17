package design.inherent.skyweavecompat.rendering;

import java.util.HashSet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CoverageFootprintTest {
    @Test void coversInterveningCellsAndBothSidesOfTheConstruct() {
        var plan = CoverageFootprint.create(1, 1, 1024, -48, 1056, 48, 4096);
        assertEquals(CoverageFootprint.Status.VALID, plan.status());
        var cells = new HashSet<>(plan.cells());
        assertTrue(cells.contains(new CoverageFootprint.Cell(32, 0)));
        assertTrue(cells.contains(new CoverageFootprint.Cell(64, -3)));
        assertTrue(cells.contains(new CoverageFootprint.Cell(64, 3)));
        assertFalse(cells.contains(new CoverageFootprint.Cell(32, 20)));
        assertTrue(plan.cells().size() < 1000);
    }

    @Test void stripTraversalCoversIndependentRaySamplesWithoutScanningTheBoundingSquare() {
        for (double sign : new double[]{-1, 1}) {
            double minimum = sign > 0 ? 1023 : -1041, maximum = minimum + 18;
            var plan = CoverageFootprint.create(sign * 3, -sign * 5, minimum, minimum, maximum, maximum, 4096);
            assertEquals(CoverageFootprint.Status.VALID, plan.status());
            assertTrue(plan.cells().size() < 1000, "A thin diagonal must not visit the whole bounding square");
            var cells = new HashSet<>(plan.cells());
            for (int x = 0; x <= 4; x++) for (int z = 0; z <= 4; z++) for (int step = 0; step <= 2048; step++) {
                double t = step / 2048.0;
                double px = sign * 3 + (minimum + x * 4.5 - sign * 3) * t;
                double pz = -sign * 5 + (minimum + z * 4.5 + sign * 5) * t;
                assertTrue(cells.contains(new CoverageFootprint.Cell((int)Math.floor(px / 16), (int)Math.floor(pz / 16))));
            }
        }
    }

    @Test void refusesInvalidOrOversizedInputsWithoutPartialCoverageCredit() {
        assertEquals(CoverageFootprint.Status.INVALID, CoverageFootprint.create(Double.NaN, 0, 0, 0, 16, 16, 4).status());
        assertEquals(CoverageFootprint.Status.INVALID, CoverageFootprint.create(0, 0, 20, 0, 10, 16, 4).status());
        var large = CoverageFootprint.create(0, 0, 10000, 10000, 20000, 20000, 4);
        assertEquals(CoverageFootprint.Status.TOO_LARGE, large.status()); assertTrue(large.cells().isEmpty());
        var local = CoverageFootprint.create(-.1, -.1, -1, -1, 1, 1, 64);
        assertEquals(CoverageFootprint.Status.VALID, local.status());
        assertTrue(local.cells().contains(new CoverageFootprint.Cell(-1, -1)));
        assertThrows(UnsupportedOperationException.class, () -> local.cells().clear());
    }
}
