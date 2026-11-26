/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.probe;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.seibel.distanthorizons.core.api.internal.SharedApi;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.level.IDhLevel;
import com.seibel.distanthorizons.core.pos.DhChunkPos;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Read only actual DH-produced data from a naturally loaded vanilla spawn column. */
final class DhUsefulDataProbe {
    private final JsonObject report = new JsonObject();
    private MinecraftServer server;
    private CompletableFuture<FullDataSourceV2> pending;
    private java.util.concurrent.ExecutorService readbackWorker;
    private int ticks, reads;
    private boolean terminal;

    DhUsefulDataProbe() {
        try {
            var root = net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().toRealPath();
            var marker = com.google.gson.JsonParser.parseString(Files.readString(root.resolve("owned-startup-fixture.json"))).getAsJsonObject();
            var output = Path.of(System.getProperty("neighbors.probe.output")).toAbsolutePath().normalize();
            if (!marker.get("owner").getAsString().equals("startup_probe.py")
                    || !marker.get("scenario").getAsString().equals("standalone") || !output.getParent().toRealPath().equals(root))
                throw new IllegalStateException("DH data observer requires its marked disposable native fixture");
        } catch (Exception failure) {
            throw new IllegalStateException("Refusing DH data observation outside its marked disposable native fixture", failure);
        }
        report.addProperty("scope", "Native server chunk-update output and decoded material; no fabricated LOD, distant generation, client upload, or performance claim");
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> server = event.getServer());
        NeoForge.EVENT_BUS.addListener(this::tick);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, (ServerStoppedEvent event) -> {
            if (server != event.getServer()) return;
            report.addProperty("actualServerStopped", true);
            report.addProperty("nativeWorldPresentAfterStop", SharedApi.tryGetDhServerWorld() != null);
            if (readbackWorker != null) {
                readbackWorker.shutdown();
                try { report.addProperty("readbackWorkerTerminated", readbackWorker.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)); }
                catch (InterruptedException failure) { Thread.currentThread().interrupt(); report.addProperty("readbackWorkerTerminated", false); }
            }
            save();
        });
    }

    private void tick(ServerTickEvent.Post event) {
        if (server != event.getServer() || terminal) return;
        try {
            if (++ticks > 2400) throw new AssertionError("No useful native data within the disposable observation window");
            if (pending != null) {
                if (!pending.isDone()) return;
                var future = pending; pending = null;
                boolean complete;
                try (var source = future.join()) { complete = source != null && !source.isEmpty && inspect(source); }
                if (complete) { report.addProperty("complete", true); finish(); return; }
            }
            if (ticks % 20 != 0) return;
            var world = SharedApi.tryGetDhServerWorld();
            if (world == null) return;
            IDhLevel selected = null;
            for (var level : world.getAllLoadedLevels()) {
                if (level.getLevelWrapper().getWrappedMcObject() == server.overworld()) {
                    if (selected != null) throw new AssertionError("Multiple DH owners for the same native level");
                    selected = level;
                }
            }
            if (selected == null) return;
            var spawn = server.overworld().getSharedSpawnPos();
            if (server.overworld().getChunkSource().getChunkNow(spawn.getX() >> 4, spawn.getZ() >> 4) == null) return;
            long pos = DhSectionPos.encodeContaining(DhSectionPos.SECTION_BLOCK_DETAIL_LEVEL,
                    new DhChunkPos(spawn.getX() >> 4, spawn.getZ() >> 4));
            report.addProperty("nativeLevel", selected.getLevelWrapper().getDhIdentifier());
            report.addProperty("nativeSection", pos);
            report.addProperty("comparisonWorldSeed", server.overworld().getSeed());
            String store = System.getProperty("neighbors.probe.dhDataStore");
            if (store == null) pending = selected.getFullDataProvider().getAsync(pos);
            else {
                if (readbackWorker == null) readbackWorker = java.util.concurrent.Executors.newSingleThreadExecutor(
                        task -> new Thread(task, "Skyweave DH saved-data observer"));
                var wrapper = selected.getLevelWrapper();
                pending = CompletableFuture.supplyAsync(() -> {
                    // Only this copied, closed store is opened. It is never registered as a live provider.
                    try (var repo = new com.seibel.distanthorizons.core.sql.repo.FullDataSourceV2Repo(
                            "jdbc:dh_sqlite", Path.of(store).resolve("DistantHorizons.sqlite").toFile());
                         var dto = repo.getByKey(pos)) {
                        if (dto == null) throw new AssertionError("Closed native store lost the useful section");
                        return dto.createDataSource(wrapper, null);
                    } catch (Exception failure) { throw new java.util.concurrent.CompletionException(failure); }
                }, readbackWorker);
                report.addProperty("closedStoreCodecReadback", true);
            }
            reads++;
        } catch (Throwable failure) {
            report.addProperty("complete", false); report.addProperty("error", failure.toString());
            failure.printStackTrace(); finish();
        }
    }

    private boolean inspect(FullDataSourceV2 source) {
        var level = server.overworld(); var spawn = level.getSharedSpawnPos();
        var column = source.getApiDataPointColumn(Math.floorMod(spawn.getX(), 64), Math.floorMod(spawn.getZ(), 64));
        var rows = new JsonArray(); int solids = 0, top = Integer.MIN_VALUE;
        for (var point : column) {
            if (point.blockStateWrapper.isAir()) continue;
            // This pinned API builds points with offset zero: add the real native level minimum.
            int bottom = level.getMinBuildHeight() + point.bottomYBlockPos;
            int ceiling = level.getMinBuildHeight() + point.topYBlockPos;
            for (int y = bottom; y < ceiling; y++) {
                var actual = level.getBlockState(new BlockPos(spawn.getX(), y, spawn.getZ()));
                if (!actual.equals(point.blockStateWrapper.getWrappedMcObject()))
                    throw new AssertionError("Decoded native LOD material differs from the loaded source at Y=" + y);
                solids++;
            }
            top = Math.max(top, ceiling);
            var row = new JsonObject(); row.addProperty("bottomY", bottom); row.addProperty("topY", ceiling);
            row.addProperty("block", point.blockStateWrapper.getSerialString());
            row.addProperty("biome", point.biomeWrapper.getSerialString()); rows.add(row);
        }
        if (solids == 0) return false;
        int actualTop = level.getHeight(Heightmap.Types.WORLD_SURFACE, spawn.getX(), spawn.getZ());
        if (top != actualTop) throw new AssertionError("Decoded native LOD top differs from the actual loaded source");
        report.add("decodedColumn", rows); report.addProperty("matchedSolidCells", solids);
        report.addProperty("actualSourceTop", actualTop); report.addProperty("sourceDataClosedAfterRead", true);
        return true;
    }

    private void finish() {
        terminal = true;
        if (pending != null) {
            pending.whenComplete((source, failure) -> { if (source != null) source.close(); }); pending = null;
        }
        if (readbackWorker != null) readbackWorker.shutdown();
        save(); server.halt(false);
    }
    private void save() {
        report.addProperty("ticks", ticks); report.addProperty("nativeReads", reads);
        try { Files.writeString(Path.of(System.getProperty("neighbors.probe.output")), new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n"); }
        catch (Exception failure) { throw new IllegalStateException("Cannot retain native DH data observation", failure); }
    }
}
