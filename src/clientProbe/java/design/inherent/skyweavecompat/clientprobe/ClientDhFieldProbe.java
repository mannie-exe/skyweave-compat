/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.clientprobe;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.seibel.distanthorizons.core.api.internal.SharedApi;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.level.IDhClientLevel;
import com.seibel.distanthorizons.core.pos.DhChunkPos;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.FlatLevelSource;

/** Validate native uploaded far data against the original flat generator, without loading chunks. */
final class ClientDhFieldProbe {
    private final Map<Integer,ConcurrentSkipListSet<Long>> uploaded=new ConcurrentHashMap<>();
    private final Map<Integer,Set<Long>> checked=new HashMap<>();
    private final JsonObject results=new JsonObject();
    private CompletableFuture<FullDataSourceV2> pending;
    private WeakReference<ClientLevel> pendingClient;
    private long pendingPosition;
    private int pendingEpoch, attempts, cursor;

    void uploaded(int epoch,long renderPosition) {
        long base=DhSectionPos.encodeContaining(DhSectionPos.SECTION_BLOCK_DETAIL_LEVEL,
                new DhChunkPos(DhSectionPos.getCenterBlockPosX(renderPosition)>>4,
                        DhSectionPos.getCenterBlockPosZ(renderPosition)>>4));
        uploaded.computeIfAbsent(epoch,ignored->new ConcurrentSkipListSet<>()).add(base);
    }

    boolean ready(Minecraft mc,int epoch,JsonObject report) {
        var server=mc.getSingleplayerServer();
        if (server==null || mc.level==null || mc.level.dimension()!=net.minecraft.world.level.Level.OVERWORLD
                || !(server.overworld().getChunkSource().getGenerator() instanceof FlatLevelSource))
            throw new AssertionError("Far-field reference requires its actual integrated native flat generator");
        var completed=checked.computeIfAbsent(epoch,ignored->new HashSet<>());
        if (pending!=null) {
            if (!pending.isDone()) return false;
            var future=pending; pending=null;
            try (var source=future.join()) {
                if (pendingEpoch!=epoch || pendingClient.get()!=mc.level)
                    throw new AssertionError("Far-field read crossed its admitted native client");
                if (source!=null && !source.isEmpty) {
                    if (source.getPos()!=pendingPosition) throw new AssertionError("Far-field native section changed");
                    var row=inspect(mc,source);
                    if (row!=null) {
                        completed.add(pendingPosition);
                        var rows=results.has(Integer.toString(epoch)) ? results.getAsJsonArray(Integer.toString(epoch)) : new JsonArray();
                        rows.add(row); results.add(Integer.toString(epoch),rows);
                    }
                }
            }
        }
        report.add("nativeFarField",results.deepCopy());
        report.addProperty("nativeFarFieldReadAttempts",attempts);
        report.addProperty("nativeFarFieldUploadedSections"+epoch,uploaded.getOrDefault(epoch,new ConcurrentSkipListSet<>()).size());
        if (completed.size()>=4) return true;
        var candidates=uploaded.get(epoch);
        if (candidates==null || candidates.isEmpty()) return false;
        var positions=candidates.toArray(Long[]::new);
        long position=positions[Math.floorMod(cursor++,positions.length)];
        if (completed.contains(position)) return false;
        // Use DH's native section geometry. This read selects no gameplay chunk or residency ticket.
        if (DhSectionPos.getChebyshevSignedBlockDistance(position,mc.player.getBlockX(),mc.player.getBlockZ())<=64)
            return false;
        var world=SharedApi.getAbstractDhWorld(); if (world==null) return false;
        IDhClientLevel owner=null;
        for (var level:world.getAllLoadedLevels())
            if (level instanceof IDhClientLevel client && level.getLevelWrapper().getWrappedMcObject()==server.overworld()) {
                if (owner!=null) throw new AssertionError("Ambiguous native far-field owner"); owner=client;
            }
        if (owner==null) return false;
        pendingPosition=position; pendingEpoch=epoch; pendingClient=new WeakReference<>(mc.level);
        pending=owner.getFullDataProvider().getAsync(position); attempts++;
        return false;
    }

    private JsonObject inspect(Minecraft mc,FullDataSourceV2 source) {
        var server=mc.getSingleplayerServer(); var level=server.overworld();
        var generator=(FlatLevelSource)level.getChunkSource().getGenerator();
        var random=level.getChunkSource().randomState();
        int x0=DhSectionPos.getMinCornerBlockX(source.getPos()),z0=DhSectionPos.getMinCornerBlockZ(source.getPos());
        var columns=new JsonArray(); int cells=0,hidden=0;
        scan: for(int x=0;x<64;x++) for(int z=0;z<64;z++) {
            int blockX=x0+x,blockZ=z0+z;
            // ClientLevel.hasChunk is unconditionally true in this native version.
            // ChunkSource.hasChunk delegates getChunk(FULL,false), so it observes real residency.
            if(mc.level.getChunkSource().hasChunk(blockX>>4,blockZ>>4)) continue;
            var column=source.getApiDataPointColumn(x,z); if(column.isEmpty()) continue;
            var decoded=new HashMap<Integer,Object>(); var runs=new JsonArray();
            for(var point:column) {
                if(point.blockStateWrapper.isAir()) continue;
                int bottom=level.getMinBuildHeight()+point.bottomYBlockPos,top=level.getMinBuildHeight()+point.topYBlockPos;
                if(bottom<level.getMinBuildHeight() || top>level.getMaxBuildHeight() || bottom>=top)
                    throw new AssertionError("Invalid far-field native interval");
                for(int y=bottom;y<top;y++) if(decoded.put(y,point.blockStateWrapper.getWrappedMcObject())!=null)
                    throw new AssertionError("Overlapping far-field native intervals");
                var row=new JsonObject();row.addProperty("bottom",bottom);row.addProperty("top",top);
                row.addProperty("block",point.blockStateWrapper.getSerialString());runs.add(row);
            }
            var original=generator.getBaseColumn(blockX,blockZ,level,random);
            for(int y=level.getMinBuildHeight();y<level.getMaxBuildHeight();y++) {
                BlockState expected=original.getBlock(y);var actual=decoded.get(y);
                if(expected.isAir() ? actual!=null : actual==null)
                    throw new AssertionError("Native far-field occupancy differs from original flat base at "+blockX+","+y+","+blockZ);
                if(!expected.isAir() && !expected.equals(actual)) {
                    if(com.seibel.distanthorizons.core.config.Config.Common.LodBuilding.worldCompression.get()
                            !=com.seibel.distanthorizons.api.enums.config.EDhApiWorldCompressionMode.VISUALLY_EQUAL || !expected.canOcclude())
                        throw new AssertionError("Unadmitted far-field material difference");
                    for(var direction:Direction.values()) {
                        var neighbor=generator.getBaseColumn(blockX+direction.getStepX(),blockZ+direction.getStepZ(),level,random);
                        if(!neighbor.getBlock(y+direction.getStepY()).canOcclude())
                            throw new AssertionError("Far-field changed an exposed original flat material");
                    }
                    hidden++;
                }
                cells++;
            }
            var row=new JsonObject();row.addProperty("x",blockX);row.addProperty("z",blockZ);
            row.addProperty("physicalClientChunkAbsent",true);
            row.addProperty("nativeClientLevelHasChunk",mc.level.hasChunk(blockX>>4,blockZ>>4));
            row.addProperty("nativeClientChunkCacheHasChunk",mc.level.getChunkSource().hasChunk(blockX>>4,blockZ>>4));
            row.add("nativeRuns",runs);columns.add(row);
            if(columns.size()==4) break scan;
        }
        if(columns.size()!=4) return null;
        var row=new JsonObject();row.addProperty("nativeSection",source.getPos());row.add("columns",columns);
        row.addProperty("comparedCells",cells);row.addProperty("nativeEnclosedMaterialDifferences",hidden);
        row.addProperty("reference","original actual FlatLevelSource.getBaseColumn; no getChunk/getBlockState/residency request");
        return row;
    }

    void retire() {
        if(pending!=null) pending.whenComplete((source,failure)->{if(source!=null)source.close();});
        pending=null;pendingClient=null;
    }

    JsonObject pendingObservation() {
        var result=new JsonObject();result.addProperty("pending",pending!=null);
        if(pending!=null) {
            result.addProperty("done",pending.isDone());result.addProperty("nativeSection",pendingPosition);
            result.addProperty("epoch",pendingEpoch);
        }
        return result;
    }
}
