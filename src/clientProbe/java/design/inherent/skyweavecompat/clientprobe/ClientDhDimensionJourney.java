/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.clientprobe;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.lang.ref.WeakReference;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Native integrated-server teleports; observe each new client's real source and uploaded data. */
final class ClientDhDimensionJourney {
    private final AtomicReference<Throwable> failure = new AtomicReference<>();
    private int step;
    private Vec3 origin;
    private float yaw, pitch;
    private JsonArray originalColumn;
    private String originalNativeId;
    private WeakReference<ClientLevel> originalClient;

    void checkFailure() {
        var problem=failure.get();
        if (problem!=null) throw new AssertionError("Native dimension fixture failed",problem);
    }

    boolean ready(Minecraft mc, JsonArray column, String nativeId, int usefulUploads, JsonObject report) {
        checkFailure();
        if (step==3) return true;
        if (nativeId==null || usefulUploads==0) return false;
        if (step==0) {
            if (mc.getSingleplayerServer()==null || mc.level.dimension()!=Level.OVERWORLD)
                throw new AssertionError("Dimension control requires its marked integrated overworld");
            origin=mc.player.position(); yaw=mc.player.getYRot(); pitch=mc.player.getXRot();
            originalColumn=column.deepCopy(); originalNativeId=nativeId;
            originalClient=new WeakReference<>(mc.level);
            report.addProperty("dimensionOriginNativeId",nativeId);
            report.add("dimensionOriginColumn",originalColumn.deepCopy());
            step=1; teleport(mc,Level.NETHER,new Vec3(.5,140,.5));
            report.addProperty("nativeNetherTeleportRequested",true); return false;
        }
        if (step==1) {
            if (mc.level.dimension()!=Level.NETHER) return false;
            if (originalClient.get()==mc.level || originalNativeId.equals(nativeId))
                throw new AssertionError("Native dimension transition reused its old client/source identity");
            report.addProperty("dimensionNetherNativeId",nativeId);
            report.addProperty("dimensionNetherUsefulUploads",usefulUploads);
            report.add("dimensionNetherColumn",column.deepCopy());
            step=2; teleport(mc,Level.OVERWORLD,origin);
            report.addProperty("nativeOverworldReturnRequested",true); return false;
        }
        if (mc.level.dimension()!=Level.OVERWORLD) return false;
        if (!originalNativeId.equals(nativeId) || !originalColumn.equals(column))
            throw new AssertionError("Returned dimension changed its native source identity or sampled terrain");
        report.addProperty("nativeDimensionRoundTripVerified",true);
        report.addProperty("dimensionReturnedUsefulUploads",usefulUploads);
        step=3; return true;
    }

    private void teleport(Minecraft mc, ResourceKey<Level> target, Vec3 position) {
        var server=mc.getSingleplayerServer();
        if (server==null || mc.player==null) throw new AssertionError("Dimension control lost its integrated server/player");
        var playerId=mc.player.getUUID();
        server.execute(() -> {
            try {
                if (server!=mc.getSingleplayerServer()) throw new AssertionError("Retired dimension fixture server");
                var player=server.getPlayerList().getPlayer(playerId); var destination=server.getLevel(target);
                if (player==null || destination==null) throw new AssertionError("Native dimension/player unavailable");
                if (!player.teleportTo(destination,position.x,position.y,position.z,Set.of(),yaw,pitch))
                    throw new AssertionError("Native teleport refused the disposable dimension control");
            } catch (Throwable problem) { failure.compareAndSet(null,problem); }
        });
    }
}
