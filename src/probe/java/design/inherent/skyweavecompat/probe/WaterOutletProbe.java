package design.inherent.skyweavecompat.probe;

import com.AdamNew.dynamicwaters.HydrologyManager;
import com.AdamNew.dynamicwaters.WatershedPath;
import com.google.gson.*;
import design.inherent.skyweavecompat.water.DrainageBoundary;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;

/** Bounded observations of published paths and already-loaded physical outlet columns. */
final class WaterOutletProbe {
    private WaterOutletProbe() {}
    private static void check(boolean valid,String message) {if(!valid)throw new AssertionError(message);}

    static JsonObject planning(MinecraftServer server,JsonObject fixture) {
        var config=fixture.getAsJsonObject("outlet");var report=new JsonObject();
        var box=config.getAsJsonArray("pathWindow");check(box.size()==4,"Outlet window requires four bounds");
        int minX=box.get(0).getAsInt(),minZ=box.get(1).getAsInt(),maxX=box.get(2).getAsInt(),maxZ=box.get(3).getAsInt();
        check(maxX>=minX && maxX-minX<=256 && maxZ>=minZ && maxZ-minZ<=256,"Unbounded outlet path window");
        var seen=Collections.newSetFromMap(new IdentityHashMap<WatershedPath,Boolean>());var paths=new ArrayList<WatershedPath>();
        for(var region:HydrologyManager.WATERSHED_REGION_CACHE.values())for(var path:region.allPaths)
            if(path.minX<=maxX && path.maxX>=minX && path.minZ<=maxZ && path.maxZ>=minZ && seen.add(path)) {
                check(paths.size()<64 && path.validNodeCount<=4096,"Outlet path budget exceeded");paths.add(path);
            }
        var junctions=new JsonArray();
        for(var value:config.getAsJsonArray("junctions")) {
            var target=value.getAsJsonArray();check(target.size()==3,"Invalid outlet junction");
            double x=target.get(0).getAsDouble(),z=target.get(1).getAsDouble();int y=target.get(2).getAsInt(),matches=0;
            for(var path:paths) {
                boolean matched=false;
                for(int i:new int[]{0,path.validNodeCount-1})if(i>=0 && Math.abs(path.segmentsX[i]-x)<1e-6 && Math.abs(path.segmentsZ[i]-z)<1e-6) {
                    check(path.segmentsY[i]==y,"Shared native junction elevation differs: "+x+","+z+" expected "+y+" actual "+path.segmentsY[i]);matched=true;
                }
                if(matched)matches++;
            }
            check(matches>=2,"Shared native junction not exercised: "+x+","+z);
            var row=new JsonObject();row.add("at",target);row.addProperty("paths",matches);junctions.add(row);
        }
        report.add("junctions",junctions);report.addProperty("publishedPaths",paths.size());
        report.addProperty("scope","Existing native cache only; exact endpoint identity, no neighboring chunk requests");
        return report;
    }

    static JsonObject flow(MinecraftServer server,JsonObject fixture) {
        return flow(server,fixture,true);
    }
    static JsonObject flow(MinecraftServer server,JsonObject fixture,boolean required) {
        var bounds=fixture.getAsJsonObject("outlet").getAsJsonArray("flowWindow");check(bounds.size()==6,"Outlet flow requires six bounds");
        int x0=bounds.get(0).getAsInt(),y0=bounds.get(1).getAsInt(),z0=bounds.get(2).getAsInt();
        int x1=bounds.get(3).getAsInt(),y1=bounds.get(4).getAsInt(),z1=bounds.get(5).getAsInt();
        check(x1>=x0 && y1>=y0 && z1>=z0 && x1-x0<=32 && y1-y0<=32 && z1-z0<=32,"Unbounded outlet flow window");
        var level=server.overworld();var generator=(SkyIslandChunkGenerator)level.getChunkSource().getGenerator();
        int chunksBefore=level.getChunkSource().getLoadedChunksCount(),columns=0,flowing=0;var cells=new JsonArray();
        for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++) {
            var chunk=level.getChunkSource().getChunkNow(Math.floorDiv(x,16),Math.floorDiv(z,16));if(chunk==null)continue;columns++;
            if(DrainageBoundary.sample(generator,level.getChunkSource().randomState(),x,z).kind()!=DrainageBoundary.Kind.VOID)continue;
            for(int y=y0;y<=y1;y++) {
                var at=new BlockPos(x,y,z);var fluid=chunk.getFluidState(at);
                if(fluid.is(net.minecraft.tags.FluidTags.WATER) && !fluid.isSource()) {
                    flowing++;if(cells.size()<16)cells.add(x+","+y+","+z+":"+chunk.getBlockState(at));
                }
            }
        }
        if(required)check(flowing>0,"Corrected mouth lost native falling-water discharge");
        var report=new JsonObject();report.addProperty("loadedColumns",columns);report.addProperty("voidFlowCells",flowing);
        report.addProperty("loadedChunksBefore",chunksBefore);report.addProperty("loadedChunksAfter",level.getChunkSource().getLoadedChunksCount());
        report.addProperty("scope","getChunkNow-only physical observation; concurrent fixture player loads may change global chunk totals");
        report.add("examples",cells);return report;
    }

    static JsonObject determinism(MinecraftServer server,JsonObject fixture) throws Exception {
        var level=server.overworld();var generator=level.getChunkSource().getGenerator();var random=level.getChunkSource().randomState();
        var point=fixture.getAsJsonArray("points").get(0).getAsJsonObject();
        int tx=Math.floorDiv(point.get("x").getAsInt(),1024),tz=Math.floorDiv(point.get("z").getAsInt(),1024);
        var tiles=List.of(new net.minecraft.world.level.ChunkPos(tx,tz),new net.minecraft.world.level.ChunkPos(tx,tz+1));
        int loaded=level.getChunkSource().getLoadedChunksCount();long began=System.nanoTime();
        java.util.function.Function<net.minecraft.world.level.ChunkPos,String> build=at->signature(com.AdamNew.dynamicwaters.terrain.BasinTile.build(at.x,at.z,
                generator,random,level.getSeed(),generator.getSeaLevel(),com.AdamNew.dynamicwaters.terrain.BasinTile.channelThreshold(
                        com.AdamNew.dynamicwaters.Config.MOUNTAIN_RIVER_DENSITY.get()),true));
        com.AdamNew.dynamicwaters.terrain.BasinTile.clearSeamCache();com.AdamNew.dynamicwaters.terrain.CoarseHeightField.reset();
        var expected=new HashMap<net.minecraft.world.level.ChunkPos,String>();for(var tile:tiles)expected.put(tile,build.apply(tile));
        for(var tile:tiles)check(build.apply(tile).equals(expected.get(tile)),"Warm native outlet tile differs: "+tile);
        com.AdamNew.dynamicwaters.terrain.BasinTile.clearSeamCache();com.AdamNew.dynamicwaters.terrain.CoarseHeightField.reset();
        for(int i=tiles.size()-1;i>=0;i--)check(build.apply(tiles.get(i)).equals(expected.get(tiles.get(i))),"Reversed cold native outlet tile differs: "+tiles.get(i));
        com.AdamNew.dynamicwaters.terrain.BasinTile.clearSeamCache();com.AdamNew.dynamicwaters.terrain.CoarseHeightField.reset();
        var workers=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var futures=tiles.stream().map(tile->workers.submit(()->build.apply(tile))).toList();
            for(int i=0;i<tiles.size();i++)check(futures.get(i).get(90,java.util.concurrent.TimeUnit.SECONDS).equals(expected.get(tiles.get(i))),
                    "Concurrent native outlet tile differs: "+tiles.get(i));
        } finally {workers.shutdownNow();check(workers.awaitTermination(15,java.util.concurrent.TimeUnit.SECONDS),"Outlet workers did not terminate");}
        check(loaded==level.getChunkSource().getLoadedChunksCount(),"Native planning-only outlet controls loaded world chunks");
        var report=new JsonObject();report.addProperty("tileBuilds",8);report.addProperty("elapsedSeconds",(System.nanoTime()-began)/1e9);
        report.add("signatures",new Gson().toJsonTree(expected.entrySet().stream().sorted(Comparator.comparing(e->e.getKey().toString()))
                .map(e->e.getKey()+":"+e.getValue()).toList()));report.addProperty("loadedChunksUnchanged",true);
        report.addProperty("scope","Actual adjacent native tiles: warm, cleared native height/seam caches, reversed order and two concurrent cold builds. Does not exhaust every production terrain cache.");
        return report;
    }
    private static String signature(com.AdamNew.dynamicwaters.terrain.BasinTile tile) {
        var rows=new ArrayList<String>();
        tile.pathsByRegion.forEach((region,paths)->paths.forEach(path->rows.add(region+":"+path.validNodeCount+":"+path.hitMainRiver+":"
                +Arrays.toString(path.segmentsX)+Arrays.toString(path.segmentsZ)+Arrays.toString(path.segmentsY)
                +Arrays.toString(path.segmentsTerrainY)+Arrays.toString(path.segmentWidths))));
        tile.lakesByRegion.forEach((region,lakes)->lakes.forEach(lake->rows.add(region+":lake:"+lake.waterY+":"+lake.floorY()+":"+lake.cellCount()
                +":"+lake.minX+":"+lake.maxX+":"+lake.minZ+":"+lake.maxZ)));
        Collections.sort(rows);
        try {return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(String.join("\n",rows).getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException error) {throw new AssertionError(error);}
    }
}
