package design.inherent.skyweavecompat.terrain.structure;

import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FoundationSupportTest {
    private static java.util.List<FoundationSupport.Foot> floor(int width, int length) {
        var result = new ArrayList<FoundationSupport.Foot>();
        for (int x=0;x<width;x++) for (int z=0;z<length;z++) result.add(new FoundationSupport.Foot(x,10,z));
        return result;
    }
    @Test void aSupportedCornerCannotQualifyAnOtherwiseFloatingHouse() {
        var result = FoundationSupport.evaluate(floor(10,10), p -> p.x()==0 ? 9 : FoundationSupport.MISSING, VillageFitSettings.DEFAULT);
        assertFalse(result.accepted()); assertEquals(10,result.supported()); assertEquals(100,result.columns());
    }
    @Test void lowReliefAndShallowAuthoredOverhangsRemainUsable() {
        assertTrue(FoundationSupport.evaluate(floor(20,20), p -> p.x()==0 ? FoundationSupport.MISSING : 9,
                VillageFitSettings.DEFAULT).accepted());
        assertTrue(FoundationSupport.evaluate(floor(5,5), p -> 8-p.x()%2, VillageFitSettings.DEFAULT).accepted());
        assertFalse(FoundationSupport.evaluate(floor(5,5), p -> 3, VillageFitSettings.DEFAULT).accepted());
    }
    @Test void sparseFootingCannotBridgeADeepGapEvenWithPermissiveCoverage() {
        var settings = new VillageFitSettings(.5,3,1,.65,8,12,16384);
        assertFalse(FoundationSupport.evaluate(floor(10,10), p -> p.x()<5 ? 9 : FoundationSupport.MISSING,settings).accepted());
        assertFalse(FoundationSupport.evaluate(floor(5,5), p -> 30,settings).accepted());
    }
    @Test void completeBuildingRootsCannotBorrowTheRoadOverhangAllowance() {
        var feet=floor(20,20);
        java.util.function.ToIntFunction<FoundationSupport.Foot> corner=foot->foot.x()==0?FoundationSupport.MISSING:9;
        assertTrue(FoundationSupport.evaluate(feet,corner,VillageFitSettings.DEFAULT,false).accepted());
        var complete=FoundationSupport.evaluate(feet,corner,VillageFitSettings.DEFAULT,true);
        assertFalse(complete.accepted());assertEquals(380,complete.supported());
        assertFalse(FoundationSupport.evaluate(feet,foot->foot.x()==0?5:9,VillageFitSettings.DEFAULT,true).accepted());
        assertTrue(FoundationSupport.evaluate(feet,foot->8-foot.x()%2,VillageFitSettings.DEFAULT,true).accepted());
    }
}
