package design.inherent.skyweavecompat.water;

import com.AdamNew.dynamicwaters.HydrologyManager;
import org.junit.jupiter.api.Test;
import org.sathrek.sky_archipelago.worldgen.generator.field.TerrainColumn;
import static org.junit.jupiter.api.Assertions.*;

class IslandWaterColumnsTest {
    private static HydrologyManager.RiverCarving lake(int bed, int water) {
        var cut = new HydrologyManager.RiverCarving();
        cut.modifies = true;
        cut.isLake = true;
        cut.targetY = bed;
        cut.waterLevel = water;
        return cut;
    }

    @Test void inclusiveTwelveBlockRoofSupportsWaterWithoutFurtherExcavation() {
        assertTrue(IslandWaterColumns.supported(lake(421, 425), new TerrainColumn(410, 421), 768));
    }

    @Test void excavatingIntoTheRequiredRoofIsRejected() {
        assertFalse(IslandWaterColumns.supported(lake(420, 425), new TerrainColumn(410, 421), 768));
    }

    @Test void unsupportedConstructionAndOutOfEnvelopeWaterAreRejected() {
        assertFalse(IslandWaterColumns.supported(lake(423, 425), new TerrainColumn(410, 421), 768));
        assertFalse(IslandWaterColumns.supported(lake(421, 768), new TerrainColumn(410, 421), 768));
    }

    @Test void lakeBedsRetainTheExistingRoofsAtTheReportedColumns() {
        int[][] columns = {{410, 421, 417, 421}, {405, 416, 417, 416}, {401, 412, 417, 412}};
        for (var column : columns) {
            var cut = lake(column[2], 425);
            assertTrue(IslandWaterColumns.fitToSupport(cut, new TerrainColumn(column[0], column[1]), 768));
            assertEquals(column[3], cut.targetY);
            assertEquals(425, cut.waterLevel);
        }
    }

    @Test void aThinRoofOrAConstrainedDryBasinDoesNotAuthorizeFoundationBuilding() {
        assertFalse(IslandWaterColumns.fitToSupport(lake(417, 425), new TerrainColumn(411, 421), 768));
        assertFalse(IslandWaterColumns.fitToSupport(lake(417, 421), new TerrainColumn(410, 421), 768));
    }

    @Test void rejectedCutsCannotRequestWaterAboveTheCallersGroundEvenBelowZero() {
        for (int originalY : new int[]{-129, -64, 0, 450, 767}) {
            var cut = IslandWaterColumns.unchanged(originalY, 768);
            assertFalse(cut.modifies);
            assertFalse(cut.isWatershedWall);
            assertEquals(originalY, cut.targetY);
            assertTrue(cut.waterLevel <= originalY, "Native post-carver repair must take its no-change branch");
            assertEquals(Integer.MIN_VALUE, cut.skinLevel);
        }
    }
}
