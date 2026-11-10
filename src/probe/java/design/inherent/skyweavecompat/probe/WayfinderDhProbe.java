package design.inherent.skyweavecompat.probe;

import com.google.gson.*;
import com.seibel.distanthorizons.common.wrappers.world.ServerLevelWrapper_neoforge;
import com.seibel.distanthorizons.api.DhApi;
import design.inherent.skyweavecompat.wayfinder.LodColumns;
import design.inherent.skyweavecompat.wayfinder.dh.DhWayfinderColumns;
import java.nio.file.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Blocks;

/** Real DH database reads from native chunks. Client sight/arrival remains a separate fixture. */
final class WayfinderDhProbe {
    private static final int[] POSITIONS = {8192,65536,-8192,29000000};
    private static boolean active;
    private static int index, ticks, staged;
    private static int markerY;
    private static Path output;
    private static JsonObject report;
    private static final JsonArray reads = new JsonArray();
    private static DhWayfinderColumns reader;
    private static boolean cancelling;
    static void start(MinecraftServer server, Path path, JsonObject evidence) throws Exception {
        output = path; report = evidence; index = ticks = 0;
        markerY = server.overworld().getMaxBuildHeight() > 640 ? 650 : 150;
        stage(server); active = true;
    }
    private static void stage(MinecraftServer server) {
        var level = server.overworld(); var wrapper = ServerLevelWrapper_neoforge.getWrapper(level);
        int x = POSITIONS[index];
        var chunk = level.getChunk(x >> 4,17 >> 4);
        level.setBlockAndUpdate(new BlockPos(x,markerY,17),Blocks.POLISHED_ANDESITE.defaultBlockState());
        var queued = DhApi.Delayed.terrainRepo.overwriteChunkDataAsync(wrapper,new Object[]{chunk,level});
        if (!queued.success) throw new AssertionError("DH rejected native import: "+queued.message);
        staged = ticks;
        reader = new DhWayfinderColumns(wrapper);
    }
    static void tick(MinecraftServer server) {
        if (!active) return;
        try {
            if (++ticks > 1200) throw new AssertionError("DH section fixture exceeded1200ticks");
            if (cancelling) {
                var result = reader.poll(POSITIONS[0],17);
                if (result.state() == LodColumns.State.PENDING) return;
                if (result.state() != LodColumns.State.READY) throw new AssertionError("Read gate did not recover after cancellation: "+result.state());
                reader.close(); report.addProperty("dhCancellationReleasesReadSlot",true);
                report.add("dhNativeColumnReads",reads); report.addProperty("complete",true);
                report.addProperty("dhScope","Native written/read columns, not a client ray or teleport test");
                save(); active = false; WayfinderProbe.verifyShutdown(server); server.halt(false); return;
            }
            var result = reader.poll(POSITIONS[index],17);
            if (result.state() == LodColumns.State.PENDING) return;
            if ((result.state() == LodColumns.State.MISSING || result.state() == LodColumns.State.READY
                    && result.bands().stream().noneMatch(band -> !band.air() && band.bottom() <=markerY && band.top()==markerY+1))
                    && ticks - staged < 200) {
                reader.close(); reader = new DhWayfinderColumns(ServerLevelWrapper_neoforge.getWrapper(server.overworld())); return;
            }
            if (result.state() != LodColumns.State.READY) {
                var owned = DhWayfinderColumns.class.getDeclaredField("source"); owned.setAccessible(true);
                var source = (com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2)owned.get(reader);
                if (source != null) {
                    var raw = source.getColumnAtRelPos(Math.floorMod(POSITIONS[index],64),17);
                    var bands = new JsonArray();
                    for (int item = 0; item < raw.size(); item++) {
                        long value = raw.getLong(item);
                        var band = new JsonObject();
                        band.addProperty("rawBottom",com.seibel.distanthorizons.core.util.FullDataPointUtil.getBottomY(value));
                        band.addProperty("height",com.seibel.distanthorizons.core.util.FullDataPointUtil.getHeight(value));
                        band.addProperty("air",source.mapping.getBlockStateWrapper(com.seibel.distanthorizons.core.util.FullDataPointUtil.getId(value)).isAir());
                        bands.add(band);
                    }
                    report.add("failedRawDhBands",bands);
                    report.addProperty("nativeMinY",server.overworld().getMinBuildHeight());
                    report.addProperty("nativeMaxY",server.overworld().getMaxBuildHeight());
                }
                throw new AssertionError("Native DH column missing: "+POSITIONS[index]+" "+result.state());
            }
            if (result.bands().stream().noneMatch(band -> !band.air() && band.bottom() <=markerY && band.top()==markerY+1))
                throw new AssertionError("DH column lost actual surface/minimum-Y offset: "+result.bands());
            if (result.bands().stream().anyMatch(band -> band.bottom() < server.overworld().getMinBuildHeight()
                    || band.top() > server.overworld().getMaxBuildHeight()))
                throw new AssertionError("DH padding escaped native build bounds");
            var row = new JsonObject(); row.addProperty("x",POSITIONS[index]); row.addProperty("top",markerY+1);
            row.addProperty("bands",result.bands().size()); reads.add(row); reader.close();
            if (++index < POSITIONS.length) stage(server);
            else {
                reader = new DhWayfinderColumns(ServerLevelWrapper_neoforge.getWrapper(server.overworld()));
                reader.poll(POSITIONS[0],17); reader.close();
                if (reader.poll(POSITIONS[0],17).state() != LodColumns.State.CLOSED) throw new AssertionError("Cancelled reader stayed usable");
                reader = new DhWayfinderColumns(ServerLevelWrapper_neoforge.getWrapper(server.overworld())); cancelling = true;
            }
        } catch (Throwable error) {
            if (reader != null) reader.close(); report.addProperty("complete",false); report.addProperty("error",error.toString());
            error.printStackTrace(); save(); active = false; server.halt(false);
        }
    }
    private static void save() {
        try { Files.writeString(output,new GsonBuilder().setPrettyPrinting().create().toJson(report)+"\n"); }
        catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
    }
}
