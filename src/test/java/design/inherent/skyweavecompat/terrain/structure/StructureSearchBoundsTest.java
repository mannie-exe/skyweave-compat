package design.inherent.skyweavecompat.terrain.structure;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StructureSearchBoundsTest {
    @Test void envelopeDistanceAgreesWithExhaustiveDiscretePoints() {
        for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++)
            for (int shift = 0; shift <= 3; shift++) {
                long expected = Long.MAX_VALUE;
                for (int dx = -shift; dx <= shift; dx++) for (int dz = -shift; dz <= shift; dz++) {
                    long a = x + dx - 1, b = z + dz + 2;
                    expected = Math.min(expected, a * a + b * b);
                }
                assertEquals(expected, StructureSearchBounds.minimumDistance(x, z, 1, -2, shift));
            }
    }

    @Test void displacedPhysicalCentersRemainCandidatesWithoutAcceptingTheWholeEnvelope() {
        assertTrue(StructureSearchBounds.minimumDistance(24, 0, 0, 0, 16) <= 8L * 8);
        assertTrue(StructureSearchBounds.minimumDistance(24, 24, 0, 0, 16) > 8L * 8);
        assertEquals(0, StructureSearchBounds.minimumDistance(-29_999_999, 29_999_999,
                -29_999_998, 29_999_998, 16));
        assertThrows(IllegalArgumentException.class, () -> StructureSearchBounds.minimumDistance(0, 0, 0, 0, -1));
    }

    @Test void sourceBoundsIncludeTheNegativeEdgeAndRejectWrappedChunkCoordinates() {
        assertTrue(StructureSearchBounds.withinWorld(-1_875_000, 1_874_999));
        assertFalse(StructureSearchBounds.withinWorld(-1_875_001, 0));
        assertFalse(StructureSearchBounds.withinWorld(1_875_000, 0));
        assertFalse(StructureSearchBounds.withinWorld(Integer.MAX_VALUE, 0));
        assertFalse(StructureSearchBounds.withinWorld(0, Integer.MIN_VALUE));
    }
}
