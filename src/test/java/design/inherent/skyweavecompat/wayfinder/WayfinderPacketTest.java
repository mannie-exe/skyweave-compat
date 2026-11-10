package design.inherent.skyweavecompat.wayfinder;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.util.*;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WayfinderPacketTest {
    @Test void queryRoundTripRetainsRayAndIndependentPolicy() {
        var packet = new WayfinderPacket(WayfinderPacket.QUERY,UUID.randomUUID(),-29000000,400,42,1,-.01,0,
                "minecraft:overworld",128,200,List.of("minecraft:fire","#minecraft:leaves"));
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try {
            WayfinderPacket.CODEC.encode(buffer,packet);
            assertEquals(packet,WayfinderPacket.CODEC.decode(buffer));
            assertEquals(0,buffer.readableBytes());
        } finally { buffer.release(); }
    }
    @Test void hostileCollectionLengthIsRejectedBeforeReadingEntries() {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try {
            WayfinderPacket.CODEC.encode(buffer,WayfinderPacket.signal(WayfinderPacket.START,new UUID(0,0)));
            // Replace the terminal empty-list length, retaining an otherwise valid envelope.
            buffer.writerIndex(buffer.writerIndex()-1); buffer.writeVarInt(Integer.MAX_VALUE);
            assertThrows(DecoderException.class,()->WayfinderPacket.CODEC.decode(buffer));
        } finally { buffer.release(); }
    }
    @Test void unknownOperationCannotBecomeARequest() {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try { buffer.writeByte(255); assertThrows(DecoderException.class,()->WayfinderPacket.CODEC.decode(buffer)); }
        finally { buffer.release(); }
    }
}
