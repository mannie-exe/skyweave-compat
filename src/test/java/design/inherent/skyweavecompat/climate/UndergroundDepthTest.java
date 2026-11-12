package design.inherent.skyweavecompat.climate;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UndergroundDepthTest {
    @Test void altitudeTranslationDoesNotChangeUndergroundClimate() {
        for (int y = -128; y <= 420; y++)
            assertEquals(UndergroundDepth.sample(y, -128, 420, 1.1),
                    UndergroundDepth.sample(y + 200, 72, 620, 1.1), 1e-12);
        assertEquals(0, UndergroundDepth.sample(420, -128, 420, 1.1));
        assertEquals(1.1, UndergroundDepth.sample(-128, -128, 420, 1.1));
        assertEquals(.55, UndergroundDepth.sample(146, -128, 420, 1.1), 1e-12);
    }

    @Test void clampsOutsideAndRejectsInvalidFramesWithoutIntegerOverflow() {
        assertEquals(0, UndergroundDepth.sample(Integer.MAX_VALUE, -128, 420, 1.1));
        assertEquals(1.1, UndergroundDepth.sample(Integer.MIN_VALUE, -128, 420, 1.1));
        assertThrows(IllegalArgumentException.class, () -> UndergroundDepth.sample(0, 4, 4, 1.1));
        assertThrows(IllegalArgumentException.class, () -> UndergroundDepth.sample(0, 0, 10, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> UndergroundDepth.sample(0, 0, 10, 0));
    }
}
