/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.clientprobe;

import com.google.gson.JsonObject;
import design.inherent.skyweavecompat.lighting.InitialClientLight;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/** Explicit gate controls using the real client level and native engine; not packet delivery proof. */
final class InitialLightLifecycleProbe {
    private InitialLightLifecycleProbe() {}
    static JsonObject run(Minecraft mc) throws Exception {
        var result=new JsonObject();
        var level=mc.level;var listener=mc.getConnection();var engine=level.getLightEngine();
        var chunk=level.getChunkSource().getChunk(-2,-4,net.minecraft.world.level.chunk.status.ChunkStatus.FULL,false);
        require(chunk!=null,"Fixture chunk must already be loaded");
        var callbacks=new ArrayList<Runnable>();var calls=new AtomicInteger();
        Runnable begin=()->InitialClientLight.incoming(listener,level,-2,-4,()->{
            InitialClientLight.decoded(chunk);
            for(int n=0;n<8;n++)engine.checkBlock(new BlockPos(-26,-59,-62));
            callbacks.add(InitialClientLight.queued(level,calls::incrementAndGet));
        });
        begin.run();
        var gate=((InitialClientLight.EngineOwner)engine).skyweave$existingInitialLight();
        require(states(gate)==1 && pending(gate)==1 && checks(gate)==1,"Repeated checks must deduplicate for one pending chunk");
        engine.runLightUpdates();require(states(gate)==1 && checks(gate)==1,"Initial callback must precede check replay");
        callbacks.get(0).run();callbacks.get(0).run();require(calls.get()==1,"Queued native body must run exactly once");
        engine.runLightUpdates();require(states(gate)==0,"Committed checks must detach");
        result.addProperty("deduplicationAndOneShot",true);
        callbacks.clear();begin.run();begin.run();
        require(states(gate)==1 && pending(gate)==2,"Overlapping packets share the actual chunk identity");
        callbacks.get(0).run();engine.runLightUpdates();
        require(states(gate)==1 && pending(gate)==1,"One remaining callback must prevent early replay");
        callbacks.get(1).run();engine.runLightUpdates();require(states(gate)==0,"All callbacks must release checks");
        result.addProperty("overlappingCallbacks",true);
        var expected=new IllegalStateException("owned native callback failure");var failing=new Runnable[1];
        InitialClientLight.incoming(listener,level,-2,-4,()->{
            InitialClientLight.decoded(chunk);engine.checkBlock(new BlockPos(-26,-59,-62));
            failing[0]=InitialClientLight.queued(level,()->{throw expected;});
        });
        try{failing[0].run();throw new AssertionError("Expected native failure");}
        catch(IllegalStateException failure){require(failure==expected,"Preserve exact native exception");}
        require(states(gate)==0,"Failed callbacks must release state");result.addProperty("failedCallback",true);
        InitialClientLight.incoming(listener,level,-2,-4,()->{
            InitialClientLight.decoded(chunk);engine.checkBlock(new BlockPos(-26,-59,-62));
        });
        require(states(gate)==0,"Unqueued same-world handler must restore native checks");engine.runLightUpdates();
        result.addProperty("unqueuedNativeFallback",true);
        callbacks.clear();begin.run();int before=calls.get();
        level.getChunkSource().drop(chunk.getPos());callbacks.get(0).run();engine.runLightUpdates();
        require(calls.get()==before && states(gate)==0,"Unloaded chunk must refuse stale callback");
        result.addProperty("nativeChunkDrop",true);
        require(!engine.hasLightWork() || states(gate)==0,"No retained repair state after drop");
        result.addProperty("scope","Direct public adapter controls with actual client ownership and native checks; callback bodies are controlled counters, not synthetic network acceptance");
        return result;
    }
    private static Object field(Object object,String name) throws Exception {
        var field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);
    }
    private static Map<?,?> map(Object gate) throws Exception{return (Map<?,?>)field(gate,"states");}
    private static int states(Object gate) throws Exception{return map(gate).size();}
    private static int pending(Object gate) throws Exception{return (int)field(map(gate).values().iterator().next(),"pending");}
    private static int checks(Object gate) throws Exception{return ((java.util.Set<?>)field(map(gate).values().iterator().next(),"checks")).size();}
    private static void require(boolean condition,String reason){if(!condition)throw new AssertionError(reason);}
}
