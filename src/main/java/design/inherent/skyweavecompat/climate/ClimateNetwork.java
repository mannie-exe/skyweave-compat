package design.inherent.skyweavecompat.climate;

import java.util.function.BiConsumer;
import net.minecraft.network.Connection;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Physical-client implementation is installed separately; no client class loads on a server. */
public final class ClimateNetwork {
    public static BiConsumer<ClimatePacket,Connection> client = (packet,connection) -> {};
    private ClimateNetwork() {}
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("climate-2").optional().playToClient(ClimatePacket.TYPE,ClimatePacket.CODEC,
                (packet,context) -> client.accept(packet,context.connection()));
    }
}
