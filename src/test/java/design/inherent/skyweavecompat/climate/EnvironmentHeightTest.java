package design.inherent.skyweavecompat.climate;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EnvironmentHeightTest {
    @Test void cloudMappingPreservesTheRelativeBuildEnvelopePosition() {
        assertEquals(192, EnvironmentHeight.cloudHeight(192, -64, 384));
        assertEquals(-128, EnvironmentHeight.cloudHeight(-64, -128, 896));
        assertEquals(768, EnvironmentHeight.cloudHeight(320, -128, 896));
        assertEquals(469.33333, EnvironmentHeight.cloudHeight(192, -128, 896), .0001);
    }
    @Test void noCloudSentinelAndInvalidEnvelopesRemainExplicit() {
        assertTrue(Float.isNaN(EnvironmentHeight.cloudHeight(Float.NaN, -128, 896)));
        assertThrows(IllegalArgumentException.class, () -> EnvironmentHeight.cloudHeight(192, 0, 0));
    }
}
