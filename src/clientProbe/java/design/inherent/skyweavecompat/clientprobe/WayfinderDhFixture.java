package design.inherent.skyweavecompat.clientprobe;

import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.common.wrappers.world.ClientLevelWrapper_neoforge;
import com.seibel.distanthorizons.common.wrappers.world.ServerLevelWrapper_neoforge;
import design.inherent.skyweavecompat.wayfinder.LodColumns;
import design.inherent.skyweavecompat.wayfinder.dh.DhWayfinderColumns;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;

/** Fixture-only DH preparation from real native corridor chunks; excluded from product behavior. */
final class WayfinderDhFixture {
    private static DhWayfinderColumns checker;
    private static int checked, extent;
    private static String observation = "idle";
    private static String targetColumn = "unobserved";
    static String observation() { return observation; }
    static String targetColumn() { return targetColumn; }
    static void importChunk(ServerLevel level, ChunkAccess chunk) {
        var result = DhApi.Delayed.terrainRepo.overwriteChunkDataAsync(ServerLevelWrapper_neoforge.getWrapper(level),new Object[]{chunk,level});
        if (!result.success) throw new IllegalStateException(result.message);
    }
    static boolean ready(Minecraft mc, int distance) {
        if (checker ==null || extent!=distance) {
            if (checker!=null) checker.close();
            checker = new DhWayfinderColumns(ClientLevelWrapper_neoforge.getWrapper(mc.level)); checked=0; extent=distance;
        }
        var column = checker.poll(checked == distance ? distance+8 : checked,8);
        observation = checked + "/" + distance + " " + column.state();
        if (column.state()==LodColumns.State.PENDING) return false;
        if (column.state()!=LodColumns.State.READY) {
            checker.close(); checker = null; return false;
        }
        if (checked == distance) targetColumn = column.bands().toString();
        if (checked == distance && column.bands().stream().noneMatch(band -> !band.air() && band.bottom() <=102 && band.top()==103)) {
            observation += " marker absent: " + column.bands(); checker.close(); checker=null; return false;
        }
        if (checked == distance) { checker.close(); checker=null; return true; }
        // Readiness checks the actual start and marker; the real query verifies the corridor.
        checked=distance;
        return false;
    }
}
