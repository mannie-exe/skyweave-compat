package design.inherent.skyweavecompat.climate;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CloudPlacementTest {
    private float height(CloudPlacement.Mode mode, float nativeHeight, double offset, double absolute) {
        return CloudPlacement.height(mode, nativeHeight, -128, 896, offset, absolute, -2032, 2031);
    }
    @Test void modesUseTheirOwnDatumAndOnlyRelativeModesUseOffset() {
        assertEquals(EnvironmentHeight.cloudHeight(192, -128, 896), height(CloudPlacement.Mode.MAPPED, 192, 0, 700));
        assertEquals(200, height(CloudPlacement.Mode.VANILLA, 192, 8, 700));
        assertEquals(EnvironmentHeight.cloudHeight(192, -128, 896) - 12, height(CloudPlacement.Mode.MAPPED, 192, -12, 700));
        assertEquals(700, height(CloudPlacement.Mode.ABSOLUTE, 192, 800, 700));
    }
    @Test void sentinelSurvivesEveryModeAndOutputCannotLeaveNativeVerticalDomain() {
        for (var mode : CloudPlacement.Mode.values()) assertTrue(Float.isNaN(height(mode, Float.NaN, 0, 192)));
        assertEquals(2031, height(CloudPlacement.Mode.VANILLA, 192, 4064, 192));
        assertEquals(-2032, height(CloudPlacement.Mode.MAPPED, 192, -4064, 192));
        assertThrows(IllegalArgumentException.class, () -> height(CloudPlacement.Mode.VANILLA, Float.POSITIVE_INFINITY, 0, 192));
        assertThrows(IllegalArgumentException.class, () -> height(CloudPlacement.Mode.ABSOLUTE, 192, 0, Double.NaN));
    }
}
