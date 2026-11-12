package design.inherent.skyweavecompat.wayfinder;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LodColumnsTest {
    @Test void omittedDepthDoesNotInvalidateObservedAirOrBecomeInventedAir() {
        var column=new LodColumns.Column(LodColumns.State.READY,List.of(
                new LodColumns.Band(128,276,false),new LodColumns.Band(276,768,true)));
        assertTrue(column.covers(350,351));
        assertTrue(column.covers(270,280));
        assertTrue(column.covers(128,768));
        assertFalse(column.covers(-128,768));
        assertFalse(column.covers(127,129));
        assertFalse(column.covers(768,768));
        assertTrue(column.covers(276,276));
    }
    @Test void internalGapsAndUnobservedStatesCannotCertifyTraversal() {
        var column=new LodColumns.Column(LodColumns.State.READY,List.of(
                new LodColumns.Band(128,150,false),new LodColumns.Band(160,768,true)));
        assertTrue(column.covers(160,768));
        assertFalse(column.covers(149,161));
        assertFalse(column.covers(150,150));
        assertFalse(LodColumns.Column.state(LodColumns.State.PENDING).covers(160,170));
        assertThrows(IllegalArgumentException.class,()->column.covers(Double.NaN,170));
        assertThrows(IllegalArgumentException.class,()->column.covers(170,160));
    }
}
