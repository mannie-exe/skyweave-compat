package design.inherent.skyweavecompat.climate;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.util.*;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClimatePacketTest {
    private static final ResourceLocation DIM = ResourceLocation.parse("minecraft:overworld");
    @Test void exactColumnPolicyAndIncarnationRoundTripWithoutASeed() {
        var epoch = UUID.randomUUID();
        var column = new ClimateColumn(List.of(new ClimateColumn.Layer(-128,400,428),new ClimateColumn.Layer(500,600,610)));
        var chunk = new ClimateChunk(-1,100,Collections.nCopies(256,column));
        for (var packet : List.of(new ClimatePacket(ClimatePacket.RESET,epoch,DIM,ClimateProfile.DEFAULT,0,0,null),
                new ClimatePacket(ClimatePacket.CHUNK,epoch,DIM,null,-1,100,chunk),
                new ClimatePacket(ClimatePacket.FORGET,epoch,DIM,null,-1,100,null))) {
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
            try {
                ClimatePacket.CODEC.encode(buffer,packet);
                assertEquals(packet,ClimatePacket.CODEC.decode(buffer));
                assertEquals(0,buffer.readableBytes());
            } finally { buffer.release(); }
        }
    }
    @Test void malformedOperationsCoordinatesAndCountsFailBeforeReadingCollections() {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try {
            buffer.writeByte(255);
            assertThrows(DecoderException.class,()->ClimatePacket.CODEC.decode(buffer));
            buffer.clear(); buffer.writeByte(ClimatePacket.CHUNK); buffer.writeUUID(UUID.randomUUID()); buffer.writeUtf(DIM.toString());
            buffer.writeVarInt(Integer.MAX_VALUE); buffer.writeVarInt(0);
            assertThrows(DecoderException.class,()->ClimatePacket.CODEC.decode(buffer));
            buffer.clear(); buffer.writeByte(ClimatePacket.CHUNK); buffer.writeUUID(UUID.randomUUID()); buffer.writeUtf(DIM.toString());
            buffer.writeVarInt(0); buffer.writeVarInt(0); buffer.writeByte(33);
            assertThrows(DecoderException.class,()->ClimatePacket.CODEC.decode(buffer));
        } finally { buffer.release(); }
    }
}
