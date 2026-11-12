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
    private static DhWayfinderColumns query;
    private static com.google.gson.JsonObject queryState;
    private static int queryTicks;
    private static double queryBottom,queryTop;
    private static com.seibel.distanthorizons.core.file.fullDatafile.RemoteFullDataSourceProvider queryRemote;
    private static final long[] watchedKeys=new long[9];
    private static final boolean[] visitedBefore=new boolean[9];
    private static DhWayfinderColumns checker;
    private static int checked, extent;
    private static String observation = "idle";
    private static String targetColumn = "unobserved";
    static String observation() { return observation; }
    static String targetColumn() { return targetColumn; }
    static void query(Minecraft mc,int x,int z,double bottom,double top) {
        if(mc.level==null || Math.abs((long)x)>29999983 || Math.abs((long)z)>29999983)
            throw new IllegalArgumentException("Expected a current world and legal column");
        if(query!=null)query.close();
        query=new DhWayfinderColumns(ClientLevelWrapper_neoforge.getWrapper(mc.level));queryTicks=0;
        queryBottom=bottom;queryTop=top;
        queryState=new com.google.gson.JsonObject();queryState.addProperty("x",x);queryState.addProperty("z",z);
        queryState.addProperty("bottom",bottom);queryState.addProperty("top",top);
        var level=com.seibel.distanthorizons.core.api.internal.SharedApi.getAbstractDhWorld()
                .getLevel(ClientLevelWrapper_neoforge.getWrapper(mc.level));
        queryRemote=level.getFullDataProvider() instanceof com.seibel.distanthorizons.core.file.fullDatafile.RemoteFullDataSourceProvider remote?remote:null;
        if(queryRemote!=null)try {
            var visited=(java.util.Set<?>)WayfinderFixture.field(queryRemote.getClass(),"visitedPositions").get(queryRemote);
            for(int detail=0;detail<watchedKeys.length;detail++) {
                watchedKeys[detail]=com.seibel.distanthorizons.core.pos.DhSectionPos.encode((byte)(6+detail),
                        Math.floorDiv(x,64<<detail),Math.floorDiv(z,64<<detail));
                visitedBefore[detail]=visited.contains(watchedKeys[detail]);
            }
            queryState.add("remoteGetVisitedBefore",new com.google.gson.Gson().toJsonTree(visitedBefore));
        } catch(Exception failure){throw new IllegalStateException("Cannot inspect pinned native refresh boundary",failure);}
    }
    static com.google.gson.JsonObject queryState() {
        if(query==null)return queryState;
        var column=query.poll(queryState.get("x").getAsInt(),queryState.get("z").getAsInt(),queryBottom,queryTop);
        queryState.addProperty("state",column.state().name());queryState.addProperty("detail",column.detail());
        queryState.addProperty("reads",query.readCount());
        if(column.state()!=LodColumns.State.PENDING) {
            if(queryRemote!=null)try {
                var visited=(java.util.Set<?>)WayfinderFixture.field(queryRemote.getClass(),"visitedPositions").get(queryRemote);
                var after=new boolean[watchedKeys.length];int newVisits=0;
                for(int i=0;i<after.length;i++){after[i]=visited.contains(watchedKeys[i]);if(!visitedBefore[i]&&after[i])newVisits++;}
                queryState.add("remoteGetVisitedAfter",new com.google.gson.Gson().toJsonTree(after));
                queryState.addProperty("newNativeRefreshVisits",newVisits);
            } catch(Exception failure){throw new IllegalStateException("Cannot inspect pinned native refresh boundary",failure);}
            queryState.add("bands",new com.google.gson.Gson().toJsonTree(column.bands()));query.close();query=null;
        } else if(++queryTicks>400) {
            queryState.addProperty("fixtureTimeout",true);query.close();query=null;
        }
        return queryState;
    }
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
