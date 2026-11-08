package design.inherent.skyweavecompat.terrain.structure;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class YungTempleRulesTest {
    @Test void exclusionUsesInclusiveChunkSquaresAndNegativeCoordinates() {
        assertTrue(YungTempleRules.near(0, 0, 111, 111, 6));
        assertFalse(YungTempleRules.near(0, 0, 112, 0, 6));
        assertTrue(YungTempleRules.near(-1, -1, -112, -112, 6));
        assertFalse(YungTempleRules.near(-1, -1, -113, -1, 6));
        assertTrue(YungTempleRules.near(-16, 15, -1, 0, 0));
        assertFalse(YungTempleRules.near(-1, 0, 0, 0, 0));
    }
}
