package design.inherent.skyweavecompat.terrain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SkylightShapeTest {
    @Test void outlinesStayWithinTheirReservationAndRetainAConnectedCore() {
        var profile = new InteriorProfile.Skylights(true, false, 12, .5, .5);
        double bound = SkylightShape.bound(profile);
        for (long seed = 0; seed < 32; seed++) {
            assertTrue(SkylightShape.contains(seed, 0, 0, profile));
            for (int degree = 0; degree < 360; degree += 5) {
                double c = Math.cos(Math.toRadians(degree)), s = Math.sin(Math.toRadians(degree));
                boolean outside = false;
                for (double r = 0; r <= bound + 2; r += .25) {
                    boolean inside = SkylightShape.contains(seed, r * c, r * s, profile);
                    if (!inside) outside = true;
                    assertFalse(outside && inside, "A radial outline must not create isolated rings");
                    if (r > bound) assertFalse(inside);
                }
            }
        }
    }
    @Test void seededShapesVaryWithoutPerColumnRandomness() {
        var profile = InteriorProfile.Skylights.INITIAL;
        int differences = 0;
        for (int x = -12; x <= 12; x++) for (int z = -12; z <= 12; z++) {
            boolean a = SkylightShape.contains(42, x, z, profile);
            assertEquals(a, SkylightShape.contains(42, x, z, profile));
            if (a != SkylightShape.contains(43, x, z, profile)) differences++;
        }
        assertTrue(differences > 0);
    }
}
