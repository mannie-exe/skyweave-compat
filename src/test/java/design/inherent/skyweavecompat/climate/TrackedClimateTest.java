package design.inherent.skyweavecompat.climate;

import java.util.Collections;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TrackedClimateTest {
    private static final ResourceLocation DIMENSION=ResourceLocation.parse("minecraft:overworld");
    private static final ClimateColumn COLUMN=new ClimateColumn(java.util.List.of(new ClimateColumn.Layer(300,500,500)));
    private static ClimatePacket reset(UUID epoch) {
        return new ClimatePacket(ClimatePacket.RESET,epoch,DIMENSION,ClimateProfile.DEFAULT,0,0,null);
    }
    private static ClimatePacket chunk(UUID epoch,int x) {
        return new ClimatePacket(ClimatePacket.CHUNK,epoch,DIMENSION,null,x,0,new ClimateChunk(x,0,Collections.nCopies(256,COLUMN)));
    }
    @Test void reconnectDimensionWorldAndStalePacketsCannotReuseContext() {
        var state=new TrackedClimate();var first=new Object();var next=new Object();var old=UUID.randomUUID();var epoch=UUID.randomUUID();
        var data=new PackedClimateChunk(chunk(old,0).chunk());
        assertFalse(state.accept(first,DIMENSION,chunk(old,0)));
        assertTrue(state.accept(first,DIMENSION,reset(old)));
        assertEquals(64,state.referenceY(first,old,data,0,500,0));
        assertNull(state.referenceY(next,old,data,0,500,0));
        assertFalse(state.accept(next,DIMENSION,chunk(old,1)));
        assertFalse(state.accept(next,ResourceLocation.parse("minecraft:the_nether"),reset(epoch)));
        assertTrue(state.accept(next,DIMENSION,reset(epoch)));
        assertFalse(state.accept(next,DIMENSION,chunk(old,0)));
        assertNull(state.referenceY(next,old,data,0,500,0));
        assertTrue(state.accept(next,DIMENSION,chunk(epoch,0)));
        assertFalse(state.accept(next,DIMENSION,new ClimatePacket(ClimatePacket.FORGET,old,DIMENSION,null,0,0,null)));
        assertEquals(64,state.referenceY(next,epoch,data,0,500,0));
        state.unloaded(first);assertTrue(state.synchronizedPolicy(next));
        state.unloaded(next);assertFalse(state.synchronizedPolicy(next));
        assertNull(state.referenceY(next,epoch,data,0,500,0));
    }
    @Test void acceptingOtherChunksDoesNotEvictAStillLoadedChunksAnswer() {
        var state=new TrackedClimate();var world=new Object();var epoch=UUID.randomUUID();
        var held=new PackedClimateChunk(chunk(epoch,0).chunk());state.accept(world,DIMENSION,reset(epoch));
        for(int i=1;i<10000;i++)assertTrue(state.accept(world,DIMENSION,chunk(epoch,i)));
        assertEquals(64,state.referenceY(world,epoch,held,0,500,0));
        var next=UUID.randomUUID();state.accept(world,DIMENSION,reset(next));
        assertNull(state.referenceY(world,epoch,held,0,500,0));
    }
}
