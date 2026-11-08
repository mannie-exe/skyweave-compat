package design.inherent.skyweavecompat.terrain;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class OceanPerimeterTest {
    @Test void internalChannelsStayInsideTheOuterSilhouetteAndQueriesArePure() {
        var calls = new AtomicInteger();
        var outline = OceanPerimeter.sample(1000, -700, 320, (x, z) -> {
            calls.incrementAndGet();
            double dx = x - 1000, dz = z + 700;
            return dx * dx / (320 * 320) + dz * dz / (240 * 240) < 1 && Math.hypot(dx, dz) > 100;
        });
        assertTrue(calls.get() <= OceanPerimeter.RAYS * OceanPerimeter.SAMPLES_PER_RAY);
        int sampled = calls.get();
        assertTrue(outline.outsideDistance(0, 0) < 0);
        assertTrue(outline.outsideDistance(200, 0) < 0);
        assertEquals(20, outline.outsideDistance(350, 0), 12);
        assertTrue(outline.outsideDistance(0, 290) > 0);
        assertEquals(outline.outsideDistance(350, 0), outline.outsideDistance(350, 0));
        assertEquals(sampled, calls.get(), "Column queries must not resample the native footprint");
    }
    @Test void missingNativeCoastCannotCreateAnApronFromNothing() {
        var outline = OceanPerimeter.sample(0, 0, 300, (x, z) -> false);
        assertEquals(Double.POSITIVE_INFINITY, outline.outsideDistance(310, 0));
    }
}
