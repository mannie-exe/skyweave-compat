package design.inherent.skyweavecompat.climate;

import design.inherent.skyweavecompat.terrain.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.WorldGenLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;

/** Bounded natural context publication; reading it never requests a chunk or hydrology plan. */
public final class ClimateRuntime {
    @FunctionalInterface public interface ClientLookup { Integer referenceY(LevelReader level, BlockPos position); }
    public static ClientLookup client = (level,position) -> null;
    private static final Map<ServerLevel,State> LEVELS = new IdentityHashMap<>();
    private record Sent(ServerPlayer player, UUID epoch) {}
    private static final Map<UUID,Sent> SENT = new HashMap<>();
    private static final Set<ClimateColumn.Unavailable.Reason> WARNED=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final class State {
        // Runtime incarnation, not another persisted world/DH cache identity.
        final UUID epoch = UUID.randomUUID();
        final com.google.common.cache.Cache<Long,ClimateChunk> chunks = com.google.common.cache.CacheBuilder.newBuilder().maximumWeight(1048576).weigher((Long key, ClimateChunk chunk) ->
                256 + chunk.columns().stream().mapToInt(column -> column.layers().size()).sum()).build();
    }
    private ClimateRuntime() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(ClimateNetwork::register);
        NeoForge.EVENT_BUS.addListener(ClimateRuntime::sent);
        NeoForge.EVENT_BUS.addListener((ChunkWatchEvent.UnWatch event) -> {
            var state = LEVELS.get(event.getLevel()); var player = event.getPlayer();
            if (state != null && player.connection.hasChannel(ClimatePacket.TYPE))
                PacketDistributor.sendToPlayer(player,new ClimatePacket(ClimatePacket.FORGET,state.epoch,
                        event.getLevel().dimension().location(),null,event.getPos().x,event.getPos().z,null));
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) -> SENT.remove(event.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent event) -> SENT.remove(event.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.level.LevelEvent.Unload event) -> {
            if (event.getLevel() instanceof ServerLevel level) {
                var removed = LEVELS.remove(level);
                if (removed != null) SENT.values().removeIf(sent -> removed.epoch.equals(sent.epoch()));
            }
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppedEvent event) -> {
            LEVELS.keySet().removeIf(level -> level.getServer() == event.getServer());
            SENT.values().removeIf(sent -> sent.player().server == event.getServer());
        });
    }

    private static void sent(ChunkWatchEvent.Sent event) {
        var level = event.getLevel(); var player = event.getPlayer();
        if (player.serverLevel() != level || !IslandClimate.applies(level) || !player.connection.hasChannel(ClimatePacket.TYPE)) return;
        var state = LEVELS.computeIfAbsent(level,ignored -> new State());
        var previous = SENT.get(player.getUUID());
        if (previous == null || previous.player() != player || !state.epoch.equals(previous.epoch())) {
            PacketDistributor.sendToPlayer(player,new ClimatePacket(ClimatePacket.RESET,state.epoch,level.dimension().location(),
                    ClimateConfiguration.profile(),0,0,null));
            SENT.put(player.getUUID(),new Sent(player,state.epoch));
        }
        var pos = event.getPos();
        try {
            var data=chunk(level,state,pos.x,pos.z);
            PacketDistributor.sendToPlayer(player,new ClimatePacket(ClimatePacket.CHUNK,state.epoch,level.dimension().location(),null,pos.x,pos.z,data));
        } catch(ClimateColumn.Unavailable unavailable) {
            unavailable(unavailable);
            // Retire stale client metadata; a failed capture must not publish an empty or partial success.
            PacketDistributor.sendToPlayer(player,new ClimatePacket(ClimatePacket.FORGET,state.epoch,level.dimension().location(),null,pos.x,pos.z,null));
        }
    }

    private static ClimateChunk chunk(ServerLevel level, State state, int cx, int cz) {
        long key = net.minecraft.world.level.ChunkPos.asLong(cx,cz);
        var cached = state.chunks.getIfPresent(key); if (cached != null) return cached;
        var result=ClimateChunk.capture(cx,cz,index->column(level,cx*16+index/16,cz*16+index%16));
        state.chunks.put(key,result);return result;
    }

    private static ClimateColumn column(ServerLevel level, int x, int z) {
        if (!(level.getChunkSource().getGenerator() instanceof SkyIslandChunkGenerator generator)
                || !((GeologicalGenerator)(Object)generator).skyweave$isEnabled()) return ClimateColumn.EMPTY;
        return column(generator,level.getChunkSource().randomState(),x,z);
    }
    private static ClimateColumn column(SkyIslandChunkGenerator generator, net.minecraft.world.level.levelgen.RandomState random, int x, int z) {
        var field = (InteriorField)(Object)((GeologicalGenerator)(Object)generator).skyweave$field(random);
        var context = field.skyweave$contextAt(x,z,generator.resolvedSkyIslandSettings());
        return context.climate();
    }

    public static BlockPos temperaturePosition(SkyIslandChunkGenerator generator,
            net.minecraft.world.level.levelgen.RandomState random, BlockPos pos) {
        if (IslandClimate.referencePosition(pos) || !((GeologicalGenerator)(Object)generator).skyweave$isEnabled()) return pos;
        Integer reference;
        try {reference=column(generator,random,pos.getX(),pos.getZ()).referenceY(pos.getY(),ClimateConfiguration.profile());}
        catch(ClimateColumn.Unavailable unavailable) {unavailable(unavailable);reference=null;}
        return IslandClimate.atReference(pos,reference == null ? 64 : reference);
    }

    public static Integer referenceY(LevelReader reader, BlockPos pos) {
        ServerLevel level = reader instanceof ServerLevel value ? value
                : reader instanceof WorldGenLevel world ? world.getLevel() : null;
        if (level == null) return client.referenceY(reader,pos);
        try {
            if(reader instanceof ServerLevel && level.getServer().isSameThread()) {
                int cx=Math.floorDiv(pos.getX(),16),cz=Math.floorDiv(pos.getZ(),16);
                if(level.getChunkSource().getChunkNow(cx,cz)==null)return null;
                // Runtime ticks reuse the same bounded immutable capture sent to tracked clients.
                // Generation workers retain their intrinsic-context path and never touch this server-owned map.
                var state=LEVELS.computeIfAbsent(level,ignored->new State());
                return chunk(level,state,cx,cz).column(pos.getX(),pos.getZ()).referenceY(pos.getY(),ClimateConfiguration.profile());
            }
            return column(level,pos.getX(),pos.getZ()).referenceY(pos.getY(),ClimateConfiguration.profile());
        } catch(ClimateColumn.Unavailable unavailable) {unavailable(unavailable);return null;}
    }
    private static void unavailable(ClimateColumn.Unavailable unavailable) {
        if(WARNED.add(unavailable.reason()))com.mojang.logging.LogUtils.getLogger().warn(
                "Natural climate capture unavailable ({}); retaining the documented missing-metadata temperature fallback, with no partial publication",
                unavailable.reason());
    }
}
