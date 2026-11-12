package design.inherent.skyweavecompat.wayfinder;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.util.UUID;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WayfinderFeedbackTest {
    private static final String DIMENSION="minecraft:overworld";
    private static WayfinderStatusPacket packet(UUID id,int sequence,WayfinderStatusPacket.Status status) {
        return new WayfinderStatusPacket(id,DIMENSION,sequence,status,WayfinderStatusPacket.Stage.CLIENT);
    }
    @Test void receiptBindsActualSessionAndWorldAndAcceptsTerminalOnlyOnce() {
        var receipt=new WayfinderFeedback();var world=new Object();var session=new Object();var id=UUID.randomUUID();
        assertFalse(receipt.accept(packet(id,0,WayfinderStatusPacket.Status.PENDING),world,session,DIMENSION));
        assertTrue(receipt.begin(id,world,session,DIMENSION));
        var pending=packet(id,1,WayfinderStatusPacket.Status.PENDING);
        assertFalse(receipt.accept(pending,new Object(),session,DIMENSION));
        assertFalse(receipt.accept(pending,world,new Object(),DIMENSION));
        assertFalse(receipt.accept(pending,world,session,"minecraft:the_end"));
        assertFalse(receipt.accept(packet(UUID.randomUUID(),1,WayfinderStatusPacket.Status.PENDING),world,session,DIMENSION));
        assertTrue(receipt.accept(pending,world,session,DIMENSION));
        assertFalse(receipt.accept(pending,world,session,DIMENSION));
        assertFalse(receipt.accept(packet(id,0,WayfinderStatusPacket.Status.PENDING),world,session,DIMENSION));
        assertTrue(receipt.accept(packet(id,2,WayfinderStatusPacket.Status.SUCCESS),world,session,DIMENSION));
        assertFalse(receipt.active());
        assertFalse(receipt.accept(packet(id,3,WayfinderStatusPacket.Status.FAILED),world,session,DIMENSION));
        assertFalse(receipt.begin(id,world,session,DIMENSION));
        assertTrue(receipt.begin(UUID.randomUUID(),world,session,DIMENSION));
        assertFalse(receipt.accept(pending,world,session,DIMENSION));
        receipt.clear();assertFalse(receipt.active());
    }
    @Test void wireRetainsTypedOutcomeAndRejectsInvalidCodesAndSequence() {
        var value=packet(UUID.randomUUID(),14,WayfinderStatusPacket.Status.MISSING);
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try {
            WayfinderStatusPacket.CODEC.encode(buffer,value);assertEquals(value,WayfinderStatusPacket.CODEC.decode(buffer));
            assertEquals(0,buffer.readableBytes());
            buffer.clear();WayfinderStatusPacket.CODEC.encode(buffer,value);buffer.setByte(buffer.writerIndex()-1,255);
            assertThrows(DecoderException.class,()->WayfinderStatusPacket.CODEC.decode(buffer));
            buffer.clear();buffer.writeUUID(value.request());buffer.writeUtf(DIMENSION,256);buffer.writeVarInt(-1);
            buffer.writeByte(0);buffer.writeByte(0);
            assertThrows(DecoderException.class,()->WayfinderStatusPacket.CODEC.decode(buffer));
        } finally {buffer.release();}
    }
}
