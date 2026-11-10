package design.inherent.skyweavecompat.wayfinder;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SightRayTest {
    @Test void viewComparisonIsIndependentOfNativeVectorMagnitude() {
        var ray = new SightRay(0,0,0,1,-1,0);
        assertTrue(ray.sameDirection(.70707,-.70707,0));
        assertTrue(ray.sameDirection(7,-7,0));
        assertFalse(ray.sameDirection(1,-.99,0));
        assertFalse(ray.sameDirection(0,0,0));
        assertFalse(ray.sameDirection(Double.NaN,0,0));
        assertFalse(ray.sameDirection(Double.POSITIVE_INFINITY,0,0));
    }
    @Test void continuationRetainsEveryCellAtNegativeCoordinates() {
        var ray = new SightRay(-2, .5, .5, -1, 0, 0);
        var cursor = ray.cursor(ray.intersect(-8, 0, 0, 8, 1, 1));
        var cells = new ArrayList<Integer>();
        while (!cursor.complete()) { cells.add(cursor.x()); cursor.advance(); }
        assertEquals(List.of(-3,-4,-5,-6,-7,-8), cells);
    }
    @Test void finiteWorldBoundsReplaceArbitraryRangeLimits() {
        var ray = new SightRay(.5, 400, .5, 1, 0, 0);
        var cursor = ray.cursor(ray.intersect(-29999984, -128, -29999984, 29999984, 768, 29999984));
        for (int i = 0; i < 32769; i++) cursor.advanceColumn();
        assertFalse(cursor.complete()); assertEquals(32769, cursor.x());
        assertTrue(cursor.end() > 29000000);
    }
    @Test void verticalAndOutsideEnvelopeRaysTerminate() {
        var ray = new SightRay(-1.5, 1000, 2.5, 0, -1, 0);
        var cursor = ray.cursor(ray.intersect(-10, -128, -10, 10, 768, 10));
        assertEquals(767, cursor.y()); assertEquals(-2, cursor.x());
        cursor.advanceColumn(); assertTrue(cursor.complete());
        assertNull(new SightRay(0,1000,0,0,1,0).intersect(-10,-128,-10,10,768,10));
    }
    @Test void diagonalTiesAndLargeCoordinatesDoNotStall() {
        var ray = new SightRay(29000000,400,-29000000,1,-.001,1);
        var cursor = ray.cursor(ray.intersect(-29999984,-128,-29999984,29999984,768,29999984));
        for (int i = 0; i < 10000 && !cursor.complete(); i++) {
            double previous = cursor.distance(); cursor.advance(); assertTrue(cursor.distance() > previous);
        }
    }
    @Test void invalidRaysFailBeforeTraversal() {
        assertThrows(IllegalArgumentException.class, () -> new SightRay(0,0,0,0,0,0));
        assertThrows(IllegalArgumentException.class, () -> new SightRay(Double.NaN,0,0,1,0,0));
        assertThrows(IllegalArgumentException.class, () -> new SightRay(0,0,0,Double.POSITIVE_INFINITY,0,0));
    }
    @Test void shallowDistantRayEntersTheTargetColumnAndBand() {
        // Actual native Minecraft view from the 32768-block client fixture.
        var ray = new SightRay(.5,200.36343515369006,8.5,
                .9999955892562866,-.0029720833990722895,1.2246414262803495e-16);
        var cursor = ray.cursor(ray.intersect(-29999984,-64,-29999984,29999984,320,29999984));
        for (int i = 0; i < 40000 && cursor.x() < 32768 && !cursor.complete(); i++) cursor.advanceColumn();
        assertEquals(32768,cursor.x()); assertEquals(8,cursor.z()); assertFalse(cursor.complete());
        var band = ray.intersect(32768,102,8,32769,103,9);
        assertNotNull(band);
        assertTrue(band.exit() > cursor.distance());
        assertTrue(Math.max(cursor.distance(),band.enter()) < cursor.columnExit());
    }
}
