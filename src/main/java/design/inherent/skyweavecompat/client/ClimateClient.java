package design.inherent.skyweavecompat.client;

import design.inherent.skyweavecompat.climate.*;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.neoforge.common.NeoForge;

/** Tracked native chunks only; no seed, client generator or separate answer-evicting cache. */
public final class ClimateClient {
    private static final TrackedClimate TRACKED=new TrackedClimate();
    // Diagnostics only. Weak keys do not retain unloaded chunks or their ClientLevel.
    private static final WeakHashMap<LevelChunk,ClimateChunkAccess.Data> OBSERVED=new WeakHashMap<>();
    public record Observation(boolean synchronizedPolicy,int chunks,int layers,Integer referenceY) {}
    public static Observation observe(net.minecraft.world.level.LevelReader level,net.minecraft.core.BlockPos pos) {
        return new Observation(TRACKED.synchronizedPolicy(level),OBSERVED.size(),
                OBSERVED.values().stream().mapToInt(data->data.chunk().layerCount()).sum(),lookup(level,pos));
    }
    private ClimateClient() {}
    private static void clearChunks() {
        OBSERVED.keySet().forEach(chunk->((ClimateChunkAccess)(Object)chunk).skyweave$climate(null));
        OBSERVED.clear();
    }
    private static void clear() { TRACKED.clear();clearChunks(); }
    public static void register() {
        ClimateNetwork.client=ClimateClient::receive;ClimateRuntime.client=ClimateClient::lookup;
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event)->clear());
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.level.LevelEvent.Unload event)->{
            if(event.getLevel() instanceof net.minecraft.client.multiplayer.ClientLevel) { TRACKED.unloaded(event.getLevel());clearChunks(); }
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.level.ChunkEvent.Unload event)->{
            if(event.getChunk() instanceof LevelChunk chunk && chunk.getLevel().isClientSide()) {
                ((ClimateChunkAccess)(Object)chunk).skyweave$climate(null);OBSERVED.remove(chunk);
            }
        });
    }
    private static Integer lookup(net.minecraft.world.level.LevelReader reader,net.minecraft.core.BlockPos pos) {
        if(!(reader instanceof net.minecraft.client.multiplayer.ClientLevel level))return null;
        var chunk=level.getChunkSource().getChunk(pos.getX()>>4,pos.getZ()>>4,ChunkStatus.FULL,false);
        if(chunk==null)return null;
        var data=((ClimateChunkAccess)(Object)chunk).skyweave$climate();
        return data==null?null:TRACKED.referenceY(level,data.epoch(),data.chunk(),pos.getX(),pos.getY(),pos.getZ());
    }
    private static void receive(ClimatePacket packet,Connection source) {
        var minecraft=Minecraft.getInstance();var current=minecraft.level;var connection=minecraft.getConnection();
        if(current==null || connection==null || connection.getConnection()!=source
                || !TRACKED.accept(current,current.dimension().location(),packet))return;
        if(packet.operation()==ClimatePacket.RESET) { clearChunks();return; }
        var chunk=current.getChunkSource().getChunk(packet.x(),packet.z(),ChunkStatus.FULL,false);
        if(packet.operation()==ClimatePacket.FORGET) {
            // Native view-center movement can hide an old storage slot before drop() runs.
            // Retire its attachment through weak observed identities, without creating a chunk.
            var iterator=OBSERVED.keySet().iterator();
            while(iterator.hasNext()) {
                var observed=iterator.next();var pos=observed.getPos();
                if(observed.getLevel()==current && pos.x==packet.x() && pos.z==packet.z()) {
                    ((ClimateChunkAccess)(Object)observed).skyweave$climate(null);iterator.remove();
                }
            }
            if(chunk!=null)((ClimateChunkAccess)(Object)chunk).skyweave$climate(null);
            return;
        }
        if(chunk==null)return;
        var data=new ClimateChunkAccess.Data(packet.epoch(),new PackedClimateChunk(packet.chunk()));
        ((ClimateChunkAccess)(Object)chunk).skyweave$climate(data);
        OBSERVED.put(chunk,data);
    }
}
