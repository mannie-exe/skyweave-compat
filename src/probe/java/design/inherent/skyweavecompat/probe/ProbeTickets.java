package design.inherent.skyweavecompat.probe;

import java.util.Map;
import java.util.Set;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.Ticket;
import design.inherent.skyweavecompat.mixin.StructureSearchCacheAccess;

/** Native ticket inspection shared by minimum and full integration fixtures. */
final class ProbeTickets {
    static void assertAbsent(MinecraftServer server, Set<String> types) throws Exception {
        var manager = ((StructureSearchCacheAccess)server.overworld().getChunkSource()).skyweave$searchTickets();
        var field = DistanceManager.class.getDeclaredField("tickets"); field.setAccessible(true);
        for (var value : ((Map<?, ?>)field.get(manager)).values()) for (var entry : (Iterable<?>)value) {
            var ticket = (Ticket<?>)entry;
            if (types.contains(ticket.getType().toString())) throw new AssertionError("Retained ticket: " + ticket.getType());
        }
    }
}
