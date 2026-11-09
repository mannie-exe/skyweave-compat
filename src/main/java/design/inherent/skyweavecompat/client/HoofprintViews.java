package design.inherent.skyweavecompat.client;

import java.util.WeakHashMap;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Render-thread session state; a reconnect cannot inherit another world's map location. */
public final class HoofprintViews {
    public record View(ResourceKey<Level> dimension, double x, double z, double scale) {}
    private static final WeakHashMap<ClientPacketListener, View> VIEWS = new WeakHashMap<>();
    private HoofprintViews() {}
    public static View get(ClientPacketListener connection) { return connection == null ? null : VIEWS.get(connection); }
    public static void save(ClientPacketListener connection, View view) { if (connection != null) VIEWS.put(connection, view); }
}
