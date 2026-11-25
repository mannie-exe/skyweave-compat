/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.clientprobe;

import com.google.gson.JsonObject;
import design.inherent.skyweavecompat.lighting.InitialClientLight;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;

/** Actual native codec and handler delivery, with only the queued callback deliberately held. */
public final class InitialLightPacketProbe {
    public static boolean holding;
    public static final List<Runnable> callbacks = new ArrayList<>();
    private InitialLightPacketProbe() {}
    public static void run(Minecraft mc,JsonObject report) throws Exception { var level = mc.level; var listener = mc.getConnection();
        var server = mc.getSingleplayerServer();
        var packets = server.submit(() -> {
            var list = new ArrayList<ClientboundLevelChunkWithLightPacket>();
            var source = server.overworld();
            for (int x=-3;x<=-1;x++) for(int z=-5;z<=-3;z++)
                list.add(new ClientboundLevelChunkWithLightPacket(source.getChunk(x,z), source.getLightEngine(), null, null));
            return list;
        }).join();
        long start=System.nanoTime(), maxReplayNanos=0; int deliveries=0, highStates=0, highChecks=0;
        Object gate = null;
        try {
            for (int round=0;round<8;round++) {
                holding=true;
                for (var packet:packets) for(int overlap=0;overlap<2;overlap++) {
                    deliver(mc,packet); deliveries++;
                    for(int n=0;n<1024;n++) level.getLightEngine().checkBlock(new BlockPos(packet.getX()*16+(n&15),-59+(n>>8),packet.getZ()*16+((n>>4)&15)));
                }
                gate=((InitialClientLight.EngineOwner)level.getLightEngine()).skyweave$existingInitialLight();
                require(states(gate)==9,"Nine loaded native identities must be pending");
                require(callbacks.size()==18,"Both actual packet callbacks must be retained");
                highStates=Math.max(highStates,states(gate)); highChecks=Math.max(highChecks,checks(gate));
                level.getLightEngine().runLightUpdates(); require(states(gate)==9,"No replay before packet data");
                holding=false;
                for(var callback:List.copyOf(callbacks))callback.run(); callbacks.clear();
                long replayStart=System.nanoTime();
                for(int n=0;n<16 && level.getLightEngine().hasLightWork();n++)level.getLightEngine().runLightUpdates();
                require(states(gate)==0 && ready(gate)==0,"Completed useful packet work must detach");
                maxReplayNanos=Math.max(maxReplayNanos,System.nanoTime()-replayStart);
            }
            var packet=packets.get(4); holding=true; deliver(mc,packet);
            var stale=callbacks.removeFirst(); var old=level.getChunk(packet.getX(),packet.getZ());
            level.getChunkSource().drop(old.getPos());
            require(states(gate)==0,"Actual chunk unload must remove pending identity");
            deliver(mc,packet); var replacement=callbacks.removeFirst();
            require(level.getChunk(packet.getX(),packet.getZ())!=old,"Native replacement must have a new identity");
            stale.run(); require(states(gate)==1,"Stale packet must not retire the replacement");
            replacement.run(); holding=false;
            for(int n=0;n<16 && level.getLightEngine().hasLightWork();n++)level.getLightEngine().runLightUpdates();
            require(states(gate)==0 && ready(gate)==0,"Replacement data must drain");
            holding=true; deliver(mc,packet); var retiring=callbacks.removeFirst();
            var respawn=server.submit(()->new net.minecraft.network.protocol.game.ClientboundRespawnPacket(
                server.getPlayerList().getPlayer(mc.player.getUUID()).createCommonSpawnInfo(server.getLevel(net.minecraft.world.level.Level.NETHER)),
                net.minecraft.network.protocol.game.ClientboundRespawnPacket.KEEP_ALL_DATA)).join();
            var bytes=new RegistryFriendlyByteBuf(Unpooled.buffer(),server.registryAccess());
            try {
                net.minecraft.network.protocol.game.ClientboundRespawnPacket.STREAM_CODEC.encode(bytes,respawn);
                net.minecraft.network.protocol.game.ClientboundRespawnPacket.STREAM_CODEC.decode(new RegistryFriendlyByteBuf(bytes,level.registryAccess())).handle(listener);
            } finally { bytes.release(); }
            require(mc.level!=level && mc.level.dimension()==net.minecraft.world.level.Level.NETHER,"Native respawn must replace client dimension");
            require((boolean)field(gate,"retired") && states(gate)==0 && ready(gate)==0,"Pending dimension data must retire");
            retiring.run();require(states(gate)==0,"Old native callback must stay retired");
            report.addProperty("nativeRespawnWithPendingCallback",true);
            report.addProperty("nativeCodecDeliveries",deliveries+3);
            report.addProperty("overlapAndReplacement",true); report.addProperty("completedBatches",8);
            report.addProperty("peakPendingChunks",highStates); report.addProperty("peakUniqueChecks",highChecks);
            report.addProperty("elapsedNanos",System.nanoTime()-start);
            report.addProperty("maximumNativeCommitReplayNanos",maxReplayNanos);
            report.addProperty("requestedChecks",8*18*1024);
            report.addProperty("scope","Original packet codec and client handler; controlled callback hold, integrated connection, not TCP or sustained wholepack throughput");

        } finally { holding=false; callbacks.clear(); }
    }
    private static void deliver(Minecraft mc,ClientboundLevelChunkWithLightPacket packet) {
        var bytes=new RegistryFriendlyByteBuf(Unpooled.buffer(),mc.getSingleplayerServer().registryAccess());
        try {
            ClientboundLevelChunkWithLightPacket.STREAM_CODEC.encode(bytes,packet);
            var decoded=ClientboundLevelChunkWithLightPacket.STREAM_CODEC.decode(new RegistryFriendlyByteBuf(bytes,mc.level.registryAccess()));
            require(bytes.readableBytes()==0,"Consume exact native packet frame");
            decoded.handle(mc.getConnection());
        } finally { bytes.release(); }
    }
    private static Object field(Object value,String name) throws Exception {
        var field=value.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(value);
    }
    private static int states(Object gate)throws Exception{return ((Map<?,?>)field(gate,"states")).size();}
    private static int ready(Object gate)throws Exception{return ((java.util.Collection<?>)field(gate,"ready")).size();}
    private static int checks(Object gate)throws Exception {
        int count=0;for(var state:((Map<?,?>)field(gate,"states")).values()) {
            var checks=(java.util.Set<?>)field(state,"checks");if(checks!=null)count+=checks.size();
        }return count;
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
