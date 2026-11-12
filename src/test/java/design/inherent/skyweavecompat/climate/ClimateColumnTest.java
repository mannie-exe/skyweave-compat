package design.inherent.skyweavecompat.climate;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClimateColumnTest {
    @Test void producerBudgetAndUnknownRefuseBeforePublishingAnyChunk() {
        var full=new ClimateColumn(java.util.stream.IntStream.range(0,32).mapToObj(i->new ClimateColumn.Layer(i*2,i*2,0)).toList());
        var reads=new java.util.concurrent.atomic.AtomicInteger();
        var failure=assertThrows(ClimateColumn.Unavailable.class,()->ClimateChunk.capture(0,0,index->{reads.incrementAndGet();return full;}));
        assertEquals(ClimateColumn.Unavailable.Reason.BUDGET,failure.reason());
        assertEquals(129,reads.get(),"Stop the producer as soon as its total budget is exceeded");
        reads.set(0);
        var unknown=new ClimateColumn.Unavailable(ClimateColumn.Unavailable.Reason.UNKNOWN);
        assertSame(unknown,assertThrows(ClimateColumn.Unavailable.class,()->ClimateChunk.capture(0,0,index->{
            reads.incrementAndGet();if(index==1)throw unknown;return full;
        })));
        assertEquals(2,reads.get());
        reads.set(0);
        assertThrows(IllegalArgumentException.class,()->ClimateChunk.capture(Integer.MAX_VALUE,0,index->{reads.incrementAndGet();return full;}));
        assertEquals(0,reads.get());
    }
    @Test void nestedThermalHelpersDoNotRemapAnAlreadyConvertedPosition() {
        var physical = new net.minecraft.core.BlockPos(12,500,-34);
        var mapped = IslandClimate.atReference(physical,100);
        assertEquals(100,mapped.getY()); assertEquals(12,mapped.getX()); assertEquals(-34,mapped.getZ());
        assertSame(mapped,IslandClimate.atReference(mapped,-200));
        assertSame(mapped,IslandClimate.temperaturePosition(mapped));
        assertEquals(500,physical.getY());
    }
    @Test void stackedBodiesAndLocalHillsRetainDifferentAltitudeFrames() {
        var column = new ClimateColumn(List.of(new ClimateColumn.Layer(0,200,200),new ClimateColumn.Layer(350,550,500)));
        assertEquals(64,column.referenceY(200,ClimateProfile.DEFAULT));
        assertEquals(114,column.referenceY(550,ClimateProfile.DEFAULT));
        assertEquals(14,column.referenceY(150,ClimateProfile.DEFAULT));
        assertEquals(114,column.referenceY(250,ClimateProfile.DEFAULT));
        assertEquals(64,column.referenceY(550,new ClimateProfile(0,256,16,1.1)));
        assertNull(column.referenceY(900,ClimateProfile.DEFAULT));
    }
    @Test void physicalAltitudeTranslationAndNegativeChunkCoordinatesAgree() {
        var low = new ClimateColumn(List.of(new ClimateColumn.Layer(0,200,200)));
        var high = new ClimateColumn(List.of(new ClimateColumn.Layer(300,500,500)));
        for (int y = 0; y < 450; y++) assertEquals(low.referenceY(y,ClimateProfile.DEFAULT),high.referenceY(y+300,ClimateProfile.DEFAULT));
        var chunk = new ClimateChunk(-1,-2,java.util.Collections.nCopies(256,low));
        assertSame(low,chunk.column(-1,-17));
        assertThrows(IllegalArgumentException.class,()->chunk.column(0,-17));
        assertThrows(IllegalArgumentException.class,()->new ClimateChunk(0,0,List.of(low)));
    }
}
