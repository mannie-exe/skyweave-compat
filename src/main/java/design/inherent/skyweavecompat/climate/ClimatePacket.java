package design.inherent.skyweavecompat.climate;

import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server-to-client tracked context; all counts and values are checked before allocation. */
public record ClimatePacket(int operation, UUID epoch, ResourceLocation dimension, ClimateProfile policy,
        int x, int z, ClimateChunk chunk) implements CustomPacketPayload {
    public static final int RESET = 0, CHUNK = 1, FORGET = 2;
    public static final Type<ClimatePacket> TYPE = new Type<>(ResourceLocation.parse("skyweave_compat:climate"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ClimatePacket> CODEC = StreamCodec.of(ClimatePacket::write,ClimatePacket::read);

    public ClimatePacket {
        java.util.Objects.requireNonNull(epoch); java.util.Objects.requireNonNull(dimension);
        if (dimension.toString().length() > 256 || operation < RESET || operation > FORGET || Math.abs((long)x) > 1875000 || Math.abs((long)z) > 1875000
                || operation == RESET && (policy == null || chunk != null)
                || operation == CHUNK && (chunk == null || chunk.x() != x || chunk.z() != z || policy != null)
                || operation == FORGET && (policy != null || chunk != null))
            throw new IllegalArgumentException("Invalid climate payload");
    }

    private static ClimatePacket read(RegistryFriendlyByteBuf buffer) {
        try {
            int operation = buffer.readUnsignedByte();
            if (operation > FORGET) throw new DecoderException("Unknown climate operation");
            var epoch = buffer.readUUID(); var dimension = ResourceLocation.parse(buffer.readUtf(256));
            if (operation == RESET) return new ClimatePacket(operation,epoch,dimension,
                    new ClimateProfile(buffer.readDouble(),buffer.readVarInt(),buffer.readVarInt(),buffer.readDouble()),0,0,null);
            int x = buffer.readVarInt(), z = buffer.readVarInt();
            if (Math.abs((long)x) > 1875000 || Math.abs((long)z) > 1875000) throw new DecoderException("Climate chunk outside world");
            if (operation == FORGET) return new ClimatePacket(operation,epoch,dimension,null,x,z,null);
            var columns = new ArrayList<ClimateColumn>(256); int total = 0;
            for (int i = 0; i < 256; i++) {
                int count = buffer.readUnsignedByte();
                if (count > ClimateColumn.MAX_LAYERS || (total += count) > ClimateChunk.MAX_TOTAL_LAYERS)
                    throw new DecoderException("Climate contributor budget exceeded");
                var layers = new ArrayList<ClimateColumn.Layer>(count);
                for (int n = 0; n < count; n++)
                    layers.add(new ClimateColumn.Layer(buffer.readVarInt(),buffer.readVarInt(),buffer.readVarInt()));
                columns.add(count == 0 ? ClimateColumn.EMPTY : new ClimateColumn(layers));
            }
            return new ClimatePacket(operation,epoch,dimension,null,x,z,new ClimateChunk(x,z,columns));
        } catch (IllegalArgumentException invalid) { throw new DecoderException("Invalid climate payload",invalid); }
    }

    private static void write(RegistryFriendlyByteBuf buffer, ClimatePacket packet) {
        buffer.writeByte(packet.operation); buffer.writeUUID(packet.epoch); buffer.writeUtf(packet.dimension.toString(),256);
        if (packet.operation == RESET) {
            buffer.writeDouble(packet.policy.altitudeScale()); buffer.writeVarInt(packet.policy.airReach());
            buffer.writeVarInt(packet.policy.minimumCaveCover()); buffer.writeDouble(packet.policy.bottomDepth());
            return;
        }
        buffer.writeVarInt(packet.x); buffer.writeVarInt(packet.z);
        if (packet.operation == FORGET) return;
        for (var column : packet.chunk.columns()) {
            buffer.writeByte(column.layers().size());
            for (var layer : column.layers()) {
                buffer.writeVarInt(layer.bottom()); buffer.writeVarInt(layer.top()); buffer.writeVarInt(layer.datum());
            }
        }
    }
    @Override public Type<ClimatePacket> type() { return TYPE; }
}
