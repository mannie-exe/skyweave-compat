package design.inherent.skyweavecompat.clientprobe;

import com.google.gson.JsonObject;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiGeneratorPlan;
import com.seibel.distanthorizons.common.wrappers.world.ClientLevelWrapper_neoforge;
import com.seibel.distanthorizons.core.api.internal.SharedApi;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.multiplayer.client.SyncOnLoadRequestQueue;
import com.seibel.distanthorizons.core.generation.tasks.ERetrievalResultState;
import com.seibel.distanthorizons.core.level.DhClientLevel;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos2D;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

/** Controlled native parent transfer from a replay server; no synthetic LODs or child-row deletion. */
final class WayfinderRemoteFixture {
    private static ClientLevel owner;
    private static DhClientLevel level;
    private static SyncOnLoadRequestQueue queue;
    private static CompletableFuture<Void> transferred;
    private static int ticks;
    private static JsonObject state;

    private static DhClientLevel current(Minecraft mc) {
        if(mc.level==null || mc.getSingleplayerServer()!=null)throw new IllegalStateException("Expected an owned TCP client");
        var world=SharedApi.getAbstractDhWorld();
        if(world==null || !(world.getLevel(ClientLevelWrapper_neoforge.getWrapper(mc.level)) instanceof DhClientLevel remote))
            throw new IllegalStateException("Expected the pinned native remote DH level");
        return remote;
    }

    static void pause(Minecraft mc) {
        if(queue!=null)throw new IllegalStateException("Parent transfer already active");
        var remote=current(mc);
        // Stop only this fixture's automatic renderer/requester before moving to the replay.
        // Native load synchronization would otherwise upgrade the intentionally coarse input.
        Config.Server.synchronizeOnLoad.set(false);
        if(remote.clientside.isRendering())remote.clientside.stopRenderer();
        Config.Common.WorldGenerator.generatorPlan.set(EDhApiGeneratorPlan.CHUNKS_ONLY);
        state=new JsonObject();state.addProperty("phase","automatic renderer paused");
    }

    static void start(Minecraft mc,JsonObject control) throws Exception {
        if(queue!=null)throw new IllegalStateException("Parent transfer already active");
        var remote=current(mc);
        if(remote.clientside.isRendering() || remote.remoteDataSourceProvider.worldGenQueueRef.get()!=null)
            throw new IllegalStateException("Pause the native renderer and wait for its retrieval queue to drain first");
        int minX=control.get("minX").getAsInt(),minZ=control.get("minZ").getAsInt();
        int maxX=control.get("maxX").getAsInt(),maxZ=control.get("maxZ").getAsInt();
        if(minX>maxX || minZ>maxZ || minX< -29999983 || minZ< -29999983 || maxX>29999983 || maxZ>29999983)
            throw new IllegalArgumentException("Invalid parent-transfer bounds");
        int firstX=Math.floorDiv(minX,128),firstZ=Math.floorDiv(minZ,128);
        int lastX=Math.floorDiv(maxX,128),lastZ=Math.floorDiv(maxZ,128);
        long count=(long)(lastX-firstX+1)*(lastZ-firstZ+1);
        if(count>16)throw new IllegalArgumentException("Parent transfer exceeds sixteen native sections");
        var network=(com.seibel.distanthorizons.core.multiplayer.client.ClientNetworkState)
                WayfinderFixture.field(DhClientLevel.class,"networkState").get(remote);
        if(!network.isReady() || network.sessionConfig.getGeneratorPlan()!=EDhApiGeneratorPlan.CHUNKS_ONLY)
            throw new IllegalStateException("Expected negotiated existing-data-only transfer");
        owner=mc.level;level=remote;ticks=0;state=new JsonObject();state.addProperty("phase","native parent transfer");
        state.addProperty("sectionDetail",7);state.addProperty("requests",count);
        state.addProperty("requestKind","native cache synchronization");
        // Existing sparse parents are eligible for native cache synchronization, not
        // generation requests, which require every parent column to be generated.
        Config.Server.synchronizeOnLoad.set(true);
        queue=new SyncOnLoadRequestQueue(level,network);
        var provider=level.remoteDataSourceProvider;
        var tasks=new ArrayList<CompletableFuture<Void>>();
        for(int x=firstX;x<=lastX;x++)for(int z=firstZ;z<=lastZ;z++) {
            long key=DhSectionPos.encode((byte)7,x,z);
            tasks.add(queue.submitRequest(key,0L).thenCompose(result -> {
                var data=result.dataSource;
                if(result.state!=ERetrievalResultState.SUCCESS || data==null) {
                    if(data!=null)data.close();
                    return CompletableFuture.failedFuture(new IllegalStateException("Native parent request: "+result.state));
                }
                try {
                    if(data.getPos()!=key)throw new IllegalStateException("Mismatched native parent response");
                    // Same ingestion and pooled-source ownership as the native remote getter.
                    return provider.updateDataSourceAsync(data).whenComplete((ignored,error)->data.close());
                } catch(Throwable error) {data.close();return CompletableFuture.failedFuture(error);}
            }));
        }
        transferred=CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new));
    }

    static void tick(Minecraft mc) {
        if(queue==null)return;
        try {
            if(mc.level!=owner || ++ticks>2000)throw new IllegalStateException("Parent transfer lost its session or timed out");
            queue.tick(new DhBlockPos2D(mc.player.blockPosition().getX(),mc.player.blockPosition().getZ()));
            state.addProperty("ticks",ticks);state.addProperty("waiting",queue.getWaitingTaskCount());
            state.addProperty("inProgress",queue.getInProgressTaskCount());
            if(!transferred.isDone())return;
            transferred.join();
            var renderedLevel=level;retire();
            // Render the received native coarse data without allowing automatic fine upgrades.
            Config.Server.synchronizeOnLoad.set(false);
            Config.Common.WorldGenerator.generatorPlan.set(EDhApiGeneratorPlan.DISABLED);
            renderedLevel.clientside.startRenderer();
            state.addProperty("phase","rendering native parents");
            state.addProperty("automaticRetrievalDisabledAfterTransfer",true);
        } catch(Throwable error) {
            state.addProperty("phase","failed");state.addProperty("error",error.toString());retire();
        }
    }

    private static void retire() {
        if(queue!=null){queue.startClosingAsync(true);queue.close();queue=null;}
        transferred=null;owner=null;level=null;
    }
    static JsonObject state() {return state;}
}
