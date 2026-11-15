package design.inherent.skyweavecompat.water;

import com.AdamNew.dynamicwaters.HydrologyManager;
import org.junit.jupiter.api.Test;
import org.sathrek.sky_archipelago.worldgen.generator.field.TerrainColumn;
import static org.junit.jupiter.api.Assertions.*;

class WaterRepairScopeTest {
    private static HydrologyManager.RiverCarving cut(boolean wall, int target, int water) {
        var cut = new HydrologyManager.RiverCarving();
        cut.modifies = true; cut.isWatershedWall = wall; cut.targetY = target; cut.waterLevel = water;
        return cut;
    }

    @Test void dryReferenceIsLocalButDoesNotMutateOrWetTheProposal() {
        var cut = cut(true, 359, -129);
        var bounds = WaterRepairScope.bounds(cut, new TerrainColumn(304, 366), 12);
        assertEquals(new WaterRepairScope.Bounds(348, 366, 359), bounds);
        for (int y : new int[]{-128, 0, 300, 347, 367}) assertFalse(bounds.contains(y));
        assertTrue(bounds.contains(348)); assertTrue(bounds.contains(359));
        assertEquals(-129, cut.waterLevel); assertTrue(cut.isWatershedWall);
    }

    @Test void wetReferenceAndRetainedCavityAndSeparateLowerBodiesStayDistinct() {
        var cut = cut(false, 421, 425);
        var bounds = WaterRepairScope.bounds(cut, new TerrainColumn(410, 421), 12);
        assertEquals(new WaterRepairScope.Bounds(410, 425, 425), bounds);
        assertFalse(bounds.contains(409)); assertFalse(bounds.contains(200));
        assertTrue(bounds.contains(421)); assertTrue(bounds.contains(425));
        assertEquals(425, cut.waterLevel);
    }

    @Test void scopesAreIdentityBoundNestedAndExceptionSafe() {
        var a = cut(true, 359, -129); var b = cut(true, 359, -129);
        assertEquals(-129, WaterRepairScope.reference(a, a.waterLevel));
        WaterRepairScope.run(null, () -> {
            WaterRepairScope.admit(1073, -544, a, new TerrainColumn(304, 366), 12);
            assertEquals(-129, WaterRepairScope.reference(a, a.waterLevel), "Dressing-only queries must not register writer authority");
            WaterRepairScope.capture(()->{
                WaterRepairScope.admit(1073, -544, a, new TerrainColumn(304, 366), 12);return null;
            });
            assertEquals(359, WaterRepairScope.reference(a, a.waterLevel));
            assertEquals(-129, WaterRepairScope.reference(b, b.waterLevel));
            assertThrows(IllegalArgumentException.class, () -> WaterRepairScope.run(null, () -> {
                assertEquals(-129, WaterRepairScope.reference(a, a.waterLevel));
                throw new IllegalArgumentException("fixture");
            }));
            assertEquals(359, WaterRepairScope.reference(a, a.waterLevel));
        });
        assertEquals(-129, WaterRepairScope.reference(a, a.waterLevel));
    }
}
