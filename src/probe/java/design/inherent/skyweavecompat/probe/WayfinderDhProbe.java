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
    private static boolean reimported;
    private static Path output;
    private static JsonObject report;
    private static final JsonArray reads = new JsonArray();
    private static DhWayfinderColumns reader;
    private static boolean cancelling;
    private static int coarsePhase;
    private static final int CORRUPT_COORDINATE = 16777216;
    private static byte[] corruptPayload;
    private static java.util.concurrent.CompletableFuture<com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2> parent;
    static void start(MinecraftServer server, Path path, JsonObject evidence) throws Exception {
        output = path; report = evidence; index = ticks = 0;
        markerY = server.overworld().getMaxBuildHeight() > 640 ? 650 : 150;
        stage(server); active = true;
    }
    private static void stage(MinecraftServer server) {
        var level = server.overworld(); var wrapper = ServerLevelWrapper_neoforge.getWrapper(level);
        int x = POSITIONS[index];
        var chunk = level.getChunk(x >> 4,17 >> 4);
        if(index==0)for(int vx=x+2;vx<=x+3;vx++)for(int vz=16;vz<=17;vz++)
            for(int y=level.getMinBuildHeight(),top=level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,vx,vz);y<top;y++)
                level.setBlockAndUpdate(new BlockPos(vx,y,vz),Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(x,markerY,17),Blocks.POLISHED_ANDESITE.defaultBlockState());
        var queued = DhApi.Delayed.terrainRepo.overwriteChunkDataAsync(wrapper,new Object[]{chunk,level});
        if (!queued.success) throw new AssertionError("DH rejected native import: "+queued.message);
        staged = ticks;reimported=false;
        reader = new DhWayfinderColumns(wrapper);
    }
    static void tick(MinecraftServer server) {
        if (!active) return;
        try {
            if (++ticks > 1200) throw new AssertionError("DH section fixture exceeded1200ticks");
            if(coarsePhase>0){coarse(server);return;}
            if (cancelling) {
                var result = reader.poll(POSITIONS[1],17);
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
                if(!reimported && ticks-staged>=40) {
                    var physical=server.overworld().getBlockState(new BlockPos(POSITIONS[index],markerY,17));
                    if(!physical.is(Blocks.POLISHED_ANDESITE))throw new AssertionError("Actual native marker changed: "+physical);
                    var level=server.overworld();var queued=DhApi.Delayed.terrainRepo.overwriteChunkDataAsync(
                            ServerLevelWrapper_neoforge.getWrapper(level),new Object[]{level.getChunk(POSITIONS[index]>>4,17>>4),level});
                    if(!queued.success)throw new AssertionError("DH rejected staged native reimport: "+queued.message);
                    reimported=true;report.addProperty("nativeImportRetriedAfterFortyTicks",true);
                }
                reader.close(); reader = new DhWayfinderColumns(ServerLevelWrapper_neoforge.getWrapper(server.overworld())); return;
            }
            if (result.state() != LodColumns.State.READY) {
                report.addProperty("failedDhReads",reader.readCount());
                throw new AssertionError("Native DH column missing: "+POSITIONS[index]+" "+result.state());
            }
            if (result.bands().stream().noneMatch(band -> !band.air() && band.bottom() <=markerY && band.top()==markerY+1))
                throw new AssertionError("DH column lost actual surface/minimum-Y offset at "+POSITIONS[index]+",17 detail"+result.detail()+": "+result.bands());
            if (result.bands().stream().anyMatch(band -> band.bottom() < server.overworld().getMinBuildHeight()
                    || band.top() > server.overworld().getMaxBuildHeight()))
                throw new AssertionError("DH padding escaped native build bounds");
            var row = new JsonObject(); row.addProperty("x",POSITIONS[index]); row.addProperty("top",markerY+1);
            row.addProperty("bands",result.bands().size());row.addProperty("detail",result.detail());reads.add(row);
            report.add("dhNativeColumnReads",reads);reader.close();
            if (++index < POSITIONS.length) stage(server);
            else {
                coarsePhase=1;parent=null;
            }
        } catch (Throwable error) {
            if (reader != null) reader.close(); report.addProperty("complete",false); report.addProperty("error",error.toString());
            error.printStackTrace(); save(); active = false; server.halt(false);
        }
    }
    private static void coarse(MinecraftServer server) {
        var wrapper=ServerLevelWrapper_neoforge.getWrapper(server.overworld());
        var provider=com.seibel.distanthorizons.core.api.internal.SharedApi.getAbstractDhWorld().getLevel(wrapper).getFullDataProvider();
        if(coarsePhase==1) {
            int detail=1,width=64<<detail;
            long key=com.seibel.distanthorizons.core.pos.DhSectionPos.encode((byte)(6+detail),Math.floorDiv(POSITIONS[0],width),0);
            if(parent==null){parent=provider.getAsync(key);return;}
            if(!parent.isDone())return;
            try(var source=parent.join()) {
                parent=null;
                if(source==null || source.isEmpty)return;
                int x=Math.floorMod(POSITIONS[0]>>detail,64),z=17>>detail;
                var data=source.getColumnAtRelPos(x,z);
                if(data==null || data.isEmpty())return;
                int step=source.columnGenerationSteps.getByte(com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2.relativePosToIndex(x,z));
                if(step<com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiWorldGenerationStep.TERRAIN.value)return;
                report.addProperty("coarseCompleteGenerationStep",step);
                int present=0;for(var column:source.dataPoints)if(column!=null && !column.isEmpty())present++;
                report.addProperty("coarseParentPresentColumns",present);
                if(present==4096)throw new AssertionError("Partial-row fixture unexpectedly covers every column");
            }
            // Only this disposable database is changed. The parent is actual importer/downsampler
            // output; retiring child rows recreates the sparse-cache condition, not invented geometry.
            for(int detailToRemove=0;detailToRemove<1;detailToRemove++) {
                int widthToRemove=64<<detailToRemove;
                provider.repo.deleteWithKey(com.seibel.distanthorizons.core.pos.DhSectionPos.encode((byte)(6+detailToRemove),
                        Math.floorDiv(POSITIONS[0],widthToRemove),Math.floorDiv(17,widthToRemove)));
            }
            reader=new DhWayfinderColumns(wrapper);coarsePhase++;return;
        }
        if(coarsePhase>=4){readOnlyFailure(server);return;}
        var column=reader.poll(coarsePhase==2?POSITIONS[0]:POSITIONS[0]+32,17);
        if(column.state()==LodColumns.State.PENDING)return;
        if(coarsePhase==2) {
            if(column.state()!=LodColumns.State.READY || column.detail()!=1
                    || column.bands().stream().noneMatch(band -> !band.air() && band.bottom()<=markerY && band.top()==markerY+1))
                throw new AssertionError("Actual coarse parent was not usable without its finest row: "+column);
            report.addProperty("nativeCoarseOnlyCellDetail",column.detail());report.addProperty("nativeCoarseOnlyReads",reader.readCount());
            var empty=reader.poll(POSITIONS[0]+2,17);
            if(empty.state()!=LodColumns.State.READY || empty.detail()!=1 || empty.bands().stream().anyMatch(band -> !band.air()))
                throw new AssertionError("Actual imported void parent did not retain qualified air: "+empty);
            report.addProperty("nativeCoarseVoidColumns",4);
            reader.close();coarsePhase=3;parent=null;reader=new DhWayfinderColumns(wrapper);
        } else {
            if(column.state()!=LodColumns.State.INCOMPLETE && column.state()!=LodColumns.State.MISSING)
                throw new AssertionError("Missing child coverage became known terrain: "+column.state());
            report.addProperty("nativePartialParentRejected",true);reader.close();
            coarsePhase=4;parent=null;
        }
    }
    private static void readOnlyFailure(MinecraftServer server) {
        var wrapper=ServerLevelWrapper_neoforge.getWrapper(server.overworld());
        var provider=com.seibel.distanthorizons.core.api.internal.SharedApi.getAbstractDhWorld().getLevel(wrapper).getFullDataProvider();
        long key=com.seibel.distanthorizons.core.pos.DhSectionPos.encode((byte)6,CORRUPT_COORDINATE>>6,CORRUPT_COORDINATE>>6);
        if(coarsePhase==4) {
            if(parent==null) {
                try(var existing=provider.repo.getByKey(key)) {
                    if(existing!=null)throw new AssertionError("Corruption control must use an absent disposable row");
                }
                corruptPayload=saveMalformed(provider,key);
                // Matched old boundary: the native getter repairs this exact row by deleting it.
                parent=provider.getAsync(key);return;
            }
            if(!parent.isDone())return;
            try(var discarded=parent.join()) {
                if(discarded!=null)throw new AssertionError("Native getter accepted malformed compression");
            }
            try(var deleted=provider.repo.getByKey(key)) {
                if(deleted!=null)throw new AssertionError("Negative native getter did not reproduce its destructive repair");
            }
            report.addProperty("nativeGetterDeletesMalformedRow",true);
            if(!java.util.Arrays.equals(corruptPayload,saveMalformed(provider,key)))
                throw new AssertionError("Matched malformed row changed between boundaries");
            reader=new DhWayfinderColumns(wrapper);coarsePhase=5;return;
        }
        var result=reader.poll(CORRUPT_COORDINATE,CORRUPT_COORDINATE,markerY,markerY+1);
        if(result.state()==LodColumns.State.PENDING)return;
        if(result.state()!=LodColumns.State.FAILED || reader.readCount()!=1)
            throw new AssertionError("Malformed stored data must fail one bounded read: "+result.state());
        try(var retained=provider.repo.getByKey(key)) {
            if(retained==null || retained.compressionModeValue!=127
                    || !java.util.Arrays.equals(corruptPayload,retained.compressedDataByteArray.toByteArray()))
                throw new AssertionError("Wayfinder preview repaired or changed the malformed row");
        }
        report.addProperty("previewMalformedRowPreserved",true);
        report.addProperty("previewMalformedRowReads",reader.readCount());
        reader.close();provider.repo.deleteWithKey(key);parent=null;corruptPayload=null;
        coarsePhase=0;reader=new DhWayfinderColumns(wrapper);
        reader.poll(POSITIONS[1],17);reader.close();
        if(reader.poll(POSITIONS[1],17).state()!=LodColumns.State.CLOSED)throw new AssertionError("Cancelled reader stayed usable");
        reader=new DhWayfinderColumns(wrapper);cancelling=true;
    }
    private static byte[] saveMalformed(com.seibel.distanthorizons.core.file.fullDatafile.V2.FullDataSourceProviderV2 provider,long key) {
        long sourceKey=com.seibel.distanthorizons.core.pos.DhSectionPos.encode((byte)6,Math.floorDiv(POSITIONS[2],64),0);
        try(var dto=provider.repo.getByKey(sourceKey)) {
            if(dto==null)throw new AssertionError("Missing qualified native input for malformed-row control");
            dto.pos=key;dto.compressionModeValue=127;
            provider.repo.save(dto);
            return dto.compressedDataByteArray.toByteArray();
        }
    }
    private static void save() {
        try { Files.writeString(output,new GsonBuilder().setPrettyPrinting().create().toJson(report)+"\n"); }
        catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
    }
}
