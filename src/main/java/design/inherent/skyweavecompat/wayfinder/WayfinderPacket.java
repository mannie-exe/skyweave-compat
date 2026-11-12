package design.inherent.skyweavecompat.wayfinder;

import io.netty.handler.codec.DecoderException;
import java.util.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** One bounded protocol; only QUERY carries server policy, never client-granted authority. */
public record WayfinderPacket(int operation, UUID request, double x, double y, double z,
        double dx, double dy, double dz, String dimension, int steps, int waitTicks, List<String> blocked) implements CustomPacketPayload {
    public static final int START = 0, QUERY = 1, HIT = 2, CANCEL = 3, ALIVE = 4, MISSING = 5, MISS = 6, FAILED = 7, INCOMPLETE = 8, CONSTRUCT = 9, BEGIN = 10;
    // NeoForge rejects a version mismatch for the same ID even when optional. A distinct
    // current ID lets older peers remain connected without pretending to support this protocol.
    public static final Type<WayfinderPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("skyweave_compat", "wayfinder_v2"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WayfinderPacket> CODEC = StreamCodec.of(
            (buffer, packet) -> packet.write(buffer), WayfinderPacket::read);

    public WayfinderPacket { blocked = List.copyOf(blocked); }
    public static WayfinderPacket signal(int operation, UUID request) {
        return new WayfinderPacket(operation, request, 0,0,0,0,0,0,"",0,0,List.of());
    }
    private static WayfinderPacket read(RegistryFriendlyByteBuf buffer) {
        int operation = buffer.readUnsignedByte();
        if (operation > BEGIN) throw new DecoderException("Unknown Wayfinder operation");
        UUID id = buffer.readUUID();
        double x = buffer.readDouble(), y = buffer.readDouble(), z = buffer.readDouble();
        double dx = buffer.readDouble(), dy = buffer.readDouble(), dz = buffer.readDouble();
        String dimension = buffer.readUtf(256); int steps = buffer.readVarInt(), wait = buffer.readVarInt(), count = buffer.readVarInt();
        if (count < 0 || count > 256) throw new DecoderException("Wayfinder blacklist exceeds 256 entries");
        var blocked = new ArrayList<String>(count);
        for (int i = 0; i < count; i++) blocked.add(buffer.readUtf(256));
        return new WayfinderPacket(operation,id,x,y,z,dx,dy,dz,dimension,steps,wait,blocked);
    }
    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeByte(operation); buffer.writeUUID(request);
        buffer.writeDouble(x); buffer.writeDouble(y); buffer.writeDouble(z);
        buffer.writeDouble(dx); buffer.writeDouble(dy); buffer.writeDouble(dz);
        buffer.writeUtf(dimension,256); buffer.writeVarInt(steps); buffer.writeVarInt(waitTicks); buffer.writeVarInt(blocked.size());
        for (var id : blocked) buffer.writeUtf(id,256);
    }
    @Override public Type<WayfinderPacket> type() { return TYPE; }
}
