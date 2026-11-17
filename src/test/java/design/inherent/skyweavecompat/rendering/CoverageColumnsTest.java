package design.inherent.skyweavecompat.rendering;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CoverageColumnsTest {
    @Test void emptyNeedsEveryColumnAndOwnershipRetainsTheExactImmutableSource() {
        byte[] input = new byte[4096]; Arrays.fill(input, (byte) CoverageColumns.State.EMPTY.ordinal());
        var columns = new CoverageColumns(17, -64, -64, 1, input);
        input[0] = 0;
        assertEquals(CoverageColumns.State.EMPTY, columns.cell(-4, -4));
        var owner = new Object(); var owned = columns.ownedBy(owner);
        assertSame(owner, owned.owner()); assertSame(columns.source(), owned.source()); assertEquals(17, owned.section());
        assertEquals(CoverageColumns.State.UNKNOWN, columns.cell(-5, -4));
        input[15 * 64 + 15] = (byte) CoverageColumns.State.READY.ordinal();
        var incomplete = new CoverageColumns(17, -64, -64, 1, input);
        assertEquals(CoverageColumns.State.UNKNOWN, incomplete.cell(-4, -4));
        input[0] = (byte) CoverageColumns.State.EMPTY.ordinal();
        assertEquals(CoverageColumns.State.READY, new CoverageColumns(17, -64, -64, 1, input).cell(-4, -4));
    }

    @Test void coarseCellsAndMalformedObservationsStayBounded() {
        byte[] input = new byte[4096]; input[0] = (byte) CoverageColumns.State.EMPTY.ordinal();
        var columns = new CoverageColumns(1, 0, 0, 32, input);
        assertEquals(CoverageColumns.State.EMPTY, columns.cell(0, 0));
        assertEquals(CoverageColumns.State.EMPTY, columns.cell(1, 1));
        assertEquals(CoverageColumns.State.UNKNOWN, columns.cell(2, 1));
        assertThrows(IllegalArgumentException.class, () -> new CoverageColumns(1, 0, 0, 3, input));
        input[0] = 3;
        assertThrows(IllegalArgumentException.class, () -> new CoverageColumns(1, 0, 0, 1, input));
    }
}
