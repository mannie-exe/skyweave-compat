package design.inherent.skyweavecompat.wayfinder;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WayfinderRegionTest {
    @Test void negativeAndFarCellsKeepTheirActualExtentAndRayInterval() {
        var negative=WayfinderRegion.containing(-1,-65,3);
        assertEquals(-8,negative.minX());assertEquals(-72,negative.minZ());assertEquals(8,negative.width());
        var ray=new SightRay(-100,400,-68,1,0,0);
        var interval=negative.interval(ray,new SightRay.Interval(0,200),-128,768);
        assertEquals(92,interval.enter());assertEquals(100,interval.exit());
        var far=WayfinderRegion.containing(29000001,0,2);
        assertNotNull(far.interval(new SightRay(0,400,1,1,0,0),new SightRay.Interval(0,30000000),-128,768));
        assertNull(negative.interval(new SightRay(-100,800,-68,1,0,0),new SightRay.Interval(0,200),-128,768));
    }
    @Test void alignmentAndDetailCannotInflateNativeWorkOrOverflowCoordinates() {
        assertThrows(IllegalArgumentException.class,()->new WayfinderRegion(1,0,3));
        assertThrows(IllegalArgumentException.class,()->new WayfinderRegion(0,0,31));
        assertThrows(IllegalArgumentException.class,()->new WayfinderRegion(Integer.MIN_VALUE,0,1));
        assertThrows(IllegalArgumentException.class,()->WayfinderRegion.containing(0,0,0));
    }
}
