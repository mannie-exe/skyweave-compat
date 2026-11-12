package design.inherent.skyweavecompat.wayfinder;

import io.netty.handler.codec.DecoderException;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Private server outcomes, with no server-authored display text or client authority. */
public record WayfinderStatusPacket(UUID request, String dimension, int sequence, Status status, Stage stage)
        implements CustomPacketPayload {
    public enum Status {
        PENDING(false), SUCCESS(true), CANCELLED(true), DENIED(true), UNAVAILABLE(true), COOLDOWN(true),
        BUSY(true), MISS(true), MISSING(true), FAILED(true), INCOMPLETE(true), CONSTRUCT(true), INVALID(true), UNSAFE(true),
        CLIENT_REQUIRED(true), OBSTRUCTED(true);
        public final boolean terminal;
        Status(boolean terminal) { this.terminal=terminal; }
        public String key() { return name().toLowerCase(java.util.Locale.ROOT); }
    }
    public enum Stage { ADMISSION, FRAMES, CLIENT, NATIVE, VERIFY_NEAR, DESTINATION, REFINEMENT, FINAL_FRAMES, LANDING }
    public static final Type<WayfinderStatusPacket> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("skyweave_compat","wayfinder_status"));
    public static final StreamCodec<RegistryFriendlyByteBuf,WayfinderStatusPacket> CODEC=StreamCodec.of(
            (buffer,packet) -> {
                buffer.writeUUID(packet.request);buffer.writeUtf(packet.dimension,256);buffer.writeVarInt(packet.sequence);
                buffer.writeByte(packet.status.ordinal());buffer.writeByte(packet.stage.ordinal());
            },buffer -> {
                var request=buffer.readUUID();var dimension=buffer.readUtf(256);int sequence=buffer.readVarInt();
                int status=buffer.readUnsignedByte(),stage=buffer.readUnsignedByte();
                if(status>=Status.values().length || stage>=Stage.values().length)
                    throw new DecoderException("Unknown Wayfinder status or stage");
                try {return new WayfinderStatusPacket(request,dimension,sequence,Status.values()[status],Stage.values()[stage]);}
                catch(IllegalArgumentException invalid){throw new DecoderException("Invalid Wayfinder status",invalid);}
            });
    public WayfinderStatusPacket {
        if(request==null || request.equals(new UUID(0,0)) || dimension==null || ResourceLocation.tryParse(dimension)==null
                || dimension.length()>256 || sequence<0 || sequence>65535 || status==null || stage==null)
            throw new IllegalArgumentException("Invalid Wayfinder status envelope");
    }
    @Override public Type<WayfinderStatusPacket> type(){return TYPE;}
}
