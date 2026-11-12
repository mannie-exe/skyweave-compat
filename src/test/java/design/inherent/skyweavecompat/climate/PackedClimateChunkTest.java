package design.inherent.skyweavecompat.climate;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PackedClimateChunkTest {
    @Test void compactStoragePreservesOrderedSelectionAtNegativeChunksAndExtremeHeights() {
        var columns=new ArrayList<ClimateColumn>();
        for(int i=0;i<256;i++) {
            var layers=new ArrayList<ClimateColumn.Layer>();
            for(int j=0;j<i%5;j++)layers.add(new ClimateColumn.Layer(-2048+j*700,Math.min(2047,-1048+j*700),-2048+j*800));
            columns.add(new ClimateColumn(layers));
        }
        var source=new ClimateChunk(-1,-2,columns);var packed=new PackedClimateChunk(source);
        assertEquals(columns.stream().mapToInt(column->column.layers().size()).sum(),packed.layerCount());
        for(var profile:List.of(ClimateProfile.DEFAULT,new ClimateProfile(0,0,16,1.1),new ClimateProfile(4,512,16,1.1)))
            for(int x=-16;x<0;x++)for(int z=-32;z<-16;z++) {
                for(int y=-2100;y<=2100;y+=17)assertEquals(source.column(x,z).referenceY(y,profile),packed.referenceY(x,y,z,profile));
                for(int y:new int[]{Integer.MIN_VALUE,Integer.MAX_VALUE,-2048,2047})
                    assertEquals(source.column(x,z).referenceY(y,profile),packed.referenceY(x,y,z,profile));
            }
        assertThrows(IllegalArgumentException.class,()->packed.referenceY(0,0,-32,ClimateProfile.DEFAULT));
    }
    @Test void extremeEndpointAndEmptyColumnsRemainExact() {
        var layer=new ClimateColumn(List.of(new ClimateColumn.Layer(-2048,2047,2047)));
        var packed=new PackedClimateChunk(new ClimateChunk(0,0,Collections.nCopies(256,layer)));
        assertEquals(layer.referenceY(-2048,ClimateProfile.DEFAULT),packed.referenceY(0,-2048,0,ClimateProfile.DEFAULT));
        assertEquals(64,packed.referenceY(0,2047,0,ClimateProfile.DEFAULT));
        var empty=new PackedClimateChunk(new ClimateChunk(0,0,Collections.nCopies(256,ClimateColumn.EMPTY)));
        assertEquals(0,empty.layerCount());assertNull(empty.referenceY(0,500,0,ClimateProfile.DEFAULT));
    }
}
