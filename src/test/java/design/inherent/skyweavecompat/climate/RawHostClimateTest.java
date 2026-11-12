package design.inherent.skyweavecompat.climate;

import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("try")
class RawHostClimateTest {
    @Test void nestedForeignAndOtherOwnedCachesRestoreIdentityAfterFailure() {
        var first=new Object();var second=new Object();
        assertFalse(RawHostClimate.owns(first));
        try(var outer=RawHostClimate.query(first)) {
            assertTrue(RawHostClimate.owns(first));assertFalse(RawHostClimate.owns(second));
            assertThrows(IllegalStateException.class,()->{
                try(var foreign=RawHostClimate.query(null)) {
                    assertFalse(RawHostClimate.owns(first));assertFalse(RawHostClimate.owns(null));
                    try(var inner=RawHostClimate.query(second)) {
                        assertTrue(RawHostClimate.owns(second));assertFalse(RawHostClimate.owns(first));
                    }
                    assertFalse(RawHostClimate.owns(second));throw new IllegalStateException("fixture");
                }
            });
            assertTrue(RawHostClimate.owns(first));
        }
        assertFalse(RawHostClimate.owns(first));assertFalse(RawHostClimate.owns(second));
    }

    @Test void workerCannotBorrowAnotherThreadsCacheAuthority() throws Exception {
        var cache=new Object();
        try(var worker=Executors.newSingleThreadExecutor();var ignored=RawHostClimate.query(cache)) {
            assertFalse(worker.submit(()->RawHostClimate.owns(cache)).get());
            assertTrue(worker.submit(()->{
                try(var local=RawHostClimate.query(cache)) {return RawHostClimate.owns(cache);}
            }).get());
            assertFalse(worker.submit(()->RawHostClimate.owns(cache)).get());
            assertTrue(RawHostClimate.owns(cache));
        }
    }
}
