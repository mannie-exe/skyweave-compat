package design.inherent.skyweavecompat.water;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MouthElevationTest {
    @Test void raisedVoidRimCannotLiftTheIncomingChannel() {
        var mouth=new MouthElevation();mouth.voidAt(257);mouth.head(254);
        assertTrue(mouth.joins());assertEquals(255,mouth.datum());assertEquals(254,mouth.incomingY());
    }
    @Test void aLowerRimRetainsItsDownstreamDropIncludingNegativeHeights() {
        for(int[] values:new int[][]{{240,254},{-80,-64},{-128,0}}) {
            var mouth=new MouthElevation();mouth.voidAt(values[0]);mouth.head(values[1]);
            assertTrue(mouth.joins());assertEquals(values[0]+1,mouth.datum());
        }
    }
    @Test void realWaterKeepsItsReceiverAndRefusesAnUphillJoin() {
        var mouth=new MouthElevation();mouth.voidAt(257);mouth.waterAt(252);mouth.head(254);
        assertTrue(mouth.joins());assertEquals(253,mouth.datum());
        mouth.waterAt(258);assertFalse(mouth.joins());assertEquals(258,mouth.receiverY());
        assertEquals(255,mouth.datum(),"Temporary calculation must not invent an uphill channel before refusal");
    }
    @Test void unboundOrReboundHeadsCannotSupplyAnElevation() {
        var mouth=new MouthElevation();assertFalse(mouth.joins());assertThrows(IllegalStateException.class,mouth::datum);
        mouth.voidAt(254);assertThrows(IllegalStateException.class,mouth::datum);
        mouth.head(256);mouth.head(256);assertThrows(IllegalStateException.class,()->mouth.head(257));
        var other=new MouthElevation();other.voidAt(10);other.head(8);
        assertEquals(255,mouth.datum());assertEquals(9,other.datum());
    }
    @Test void invalidNativeExtremeCannotOverflowToAnotherHeight() {
        var mouth=new MouthElevation();mouth.voidAt(Integer.MAX_VALUE);mouth.head(Integer.MAX_VALUE);
        assertThrows(ArithmeticException.class,mouth::datum);
    }
}
