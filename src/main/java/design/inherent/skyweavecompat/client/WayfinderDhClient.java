package design.inherent.skyweavecompat.client;

import com.seibel.distanthorizons.api.DhApi;
import design.inherent.skyweavecompat.wayfinder.LodColumns;
import design.inherent.skyweavecompat.wayfinder.dh.DhWayfinderColumns;
import net.minecraft.client.Minecraft;

/** Loaded only after the physical-client exact DH feature has been selected. */
final class WayfinderDhClient {
    private WayfinderDhClient() {}
    static LodColumns open() {
        if (DhApi.Delayed.worldProxy == null) return null;
        var level = DhApi.Delayed.worldProxy.getSinglePlayerLevel();
        if (level == null || level.getWrappedMcObject() != Minecraft.getInstance().level) return null;
        return new DhWayfinderColumns(level);
    }
}
