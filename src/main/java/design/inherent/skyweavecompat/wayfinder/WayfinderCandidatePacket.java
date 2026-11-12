package design.inherent.skyweavecompat.wayfinder;

import io.netty.handler.codec.DecoderException;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** A bounded observed cell, never a client-authorized teleport coordinate. */
public record WayfinderCandidatePacket(UUID request,WayfinderRegion region) implements CustomPacketPayload {
    public static final Type<WayfinderCandidatePacket> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("skyweave_compat","wayfinder_candidate"));
    public static final StreamCodec<RegistryFriendlyByteBuf,WayfinderCandidatePacket> CODEC=StreamCodec.of(
            (buffer,packet) -> {buffer.writeUUID(packet.request);buffer.writeInt(packet.region.minX());
                buffer.writeInt(packet.region.minZ());buffer.writeByte(packet.region.detail());},
            buffer -> {
                var id=buffer.readUUID();int x=buffer.readInt(),z=buffer.readInt(),detail=buffer.readUnsignedByte();
                try{return new WayfinderCandidatePacket(id,new WayfinderRegion(x,z,detail));}
                catch(IllegalArgumentException invalid){throw new DecoderException("Invalid Wayfinder cell",invalid);}
            });
    public WayfinderCandidatePacket {
        if(request==null || request.equals(new UUID(0,0)) || region==null)throw new IllegalArgumentException("Invalid Wayfinder candidate");
    }
    @Override public Type<WayfinderCandidatePacket> type(){return TYPE;}
}
