package design.inherent.skyweavecompat.probe;

import com.google.common.cache.Cache;
import com.google.gson.JsonObject;
import design.inherent.skyweavecompat.terrain.*;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.server.level.ServerLevel;
import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;

/** Native metadata cache pressure and parallel queries; no chunk requests or terrain writes. */
final class ContextCacheProbe {
    private record Value(List<IslandContext.Body> bodies,
            design.inherent.skyweavecompat.climate.ClimateColumn climate,
            List<IslandContext.Contribution> points) {}
    private static Value value(IslandContext context) {
        var points=new ArrayList<IslandContext.Contribution>();
        for(var body:context.bodies()) for(int y:new int[]{body.exterior().bottomY(),
                (body.exterior().bottomY()+body.exterior().topY())/2,body.exterior().topY()})
            points.addAll(context.contributions(y));
        return new Value(context.bodies(),context.climate(),List.copyOf(points));
    }
    private static void check(boolean valid,String message) { if(!valid)throw new AssertionError(message); }
    private ContextCacheProbe() {}

    @SuppressWarnings("unchecked")
    static JsonObject run(ServerLevel level,int x,int z) throws Exception {
        var generator=(SkyIslandChunkGenerator)level.getChunkSource().getGenerator();
        var field=((GeologicalGenerator)(Object)generator).skyweave$field(level.getChunkSource().randomState());
        var contextField=(InteriorField)(Object)field;
        var settings=generator.resolvedSkyIslandSettings();
        var access=field.getClass().getDeclaredField("skyweave$contexts"); access.setAccessible(true);
        var cache=(Cache<IslandContext.Key,IslandContext>)access.get(field);
        cache.invalidateAll();
        int loaded=level.getChunkSource().getLoadedChunksCount();
        var values=new ArrayList<Value>();long began=System.nanoTime();
        for(int i=0;i<4096;i++) values.add(value(contextField.skyweave$contextAt(x+i%64,z+i/64,settings)));
        long cold=System.nanoTime()-began;cache.cleanUp();
        check(cache.size()<=2048,"Native context cache exceeded its declared bound");
        int evicted=0;
        for(int i=0;i<values.size();i++)if(cache.getIfPresent(new IslandContext.Key(x+i%64,z+i/64,settings))==null)evicted++;
        check(evicted>=2048,"Cache pressure did not exercise actual eviction");
        began=System.nanoTime();
        for(int i=values.size()-1;i>=0;i--)
            check(values.get(i).equals(value(contextField.skyweave$contextAt(x+i%64,z+i/64,settings))),
                    "Reverse/evicted native context changed at "+i);
        long reverse=System.nanoTime()-began;
        var expected=values.getFirst();cache.invalidateAll();
        var barrier=new CyclicBarrier(4);var workers=Executors.newFixedThreadPool(4);
        began=System.nanoTime();
        try {
            var tasks=new ArrayList<Callable<Value>>();
            for(int i=0;i<4;i++)tasks.add(()->{
                barrier.await(10,TimeUnit.SECONDS);
                return value(contextField.skyweave$contextAt(x,z,settings));
            });
            for(var future:workers.invokeAll(tasks,15,TimeUnit.SECONDS))
                check(!future.isCancelled() && expected.equals(future.get()),"Concurrent cold native context changed or timed out");
        } finally { workers.shutdownNow(); }
        long parallel=System.nanoTime()-began;
        var first=contextField.skyweave$contextAt(x,z,settings);began=System.nanoTime();
        for(int i=0;i<10000;i++)check(contextField.skyweave$contextAt(x,z,settings)==first,"Warm native cache unexpectedly recomputed");
        long warm=System.nanoTime()-began;
        check(level.getChunkSource().getLoadedChunksCount()==loaded,"Native context tests loaded chunks");
        var result=new JsonObject();result.addProperty("distinctColumns",4096);result.addProperty("evictedColumns",evicted);
        result.addProperty("coldNanos",cold);result.addProperty("reverseNanos",reverse);
        result.addProperty("parallelColdQueries",4);result.addProperty("parallelNanos",parallel);
        result.addProperty("warmQueries",10000);result.addProperty("warmNanos",warm);
        result.addProperty("doesNotLoadChunks",true);
        result.addProperty("scope","Real native field metadata; simultaneous released cold callers, not proof all four held inside evaluation; not whole terrain-generation overhead.");
        return result;
    }
}
