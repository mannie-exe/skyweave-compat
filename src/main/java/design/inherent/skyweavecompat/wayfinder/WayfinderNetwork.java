package design.inherent.skyweavecompat.wayfinder;

import java.util.function.Consumer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Client implementation is installed only by the physical-client entry point. */
public final class WayfinderNetwork {
    public static Consumer<WayfinderPacket> client = ignored -> {};
    private WayfinderNetwork() {}
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional().playBidirectional(WayfinderPacket.TYPE, WayfinderPacket.CODEC, (packet, context) -> {
            if (context.player() instanceof ServerPlayer player) Wayfinder.receive(player, packet);
            else client.accept(packet);
        });
    }
}
