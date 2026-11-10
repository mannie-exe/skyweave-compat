package design.inherent.skyweavecompat.wayfinder;

import design.inherent.skyweavecompat.configuration.ConfigLocations;
import design.inherent.skyweavecompat.mixin.StructureSearchCacheAccess;
import design.inherent.skyweavecompat.terrain.LocatorText;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server authority, request lifetimes and bounded native destination leases. */
public final class Wayfinder {
    private static final Map<MinecraftServer, State> SERVERS = new IdentityHashMap<>();
    private static final TicketType<UUID> TICKET = TicketType.create("skyweave_wayfinder", UUID::compareTo);
    private static final int TICKET_LEVEL = ChunkLevel.byStatus(ChunkStatus.FULL);
    private enum Phase { FRAMES, CLIENT, NATIVE, VERIFY_NEAR, DESTINATION, FINAL_FRAMES }
    private static final class State {
        final WayfinderSettings settings;
        final Map<UUID, Job> jobs = new HashMap<>();
        final Map<UUID, Long> warps = new HashMap<>();
        final ThreadPoolExecutor worker = new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(32), task -> { var thread = new Thread(task,"Skyweave Wayfinder chunks"); thread.setDaemon(true); return thread; });
        long clock;
        int requests;
        State(WayfinderSettings settings) { this.settings = settings; }
    }
    private static final class Job {
        final UUID id = UUID.randomUUID();
        final ServerPlayer player;
        final ServerLevel level;
        final SightRay ray;
        final SightRay.Cursor cursor;
        final Set<ChunkPos> leases = new HashSet<>();
        Phase phase;
        WayfinderFrames frames;
        long touched;
        volatile boolean running = true;
        BlockPos target;
        Vec3 observed;
        Job(ServerPlayer player, long clock) {
            this.player = player; level = player.serverLevel(); touched = clock;
            var origin = player.getEyePosition(); var direction = player.getViewVector(1);
            ray = new SightRay(origin.x,origin.y,origin.z,direction.x,direction.y,direction.z);
            cursor = ray.cursor(domain(ray,level));
            frames = WayfinderFrames.open(level,ray);
        }
    }
    private Wayfinder() {}
    public static SightRay.Interval domain(SightRay ray, net.minecraft.world.level.Level level) {
        var border = level.getWorldBorder();
        return ray.intersect(Math.max(-30000000,border.getMinX()),level.getMinBuildHeight(),Math.max(-30000000,border.getMinZ()),
                Math.min(30000000,border.getMaxX()),level.getMaxBuildHeight(),Math.min(30000000,border.getMaxZ()));
    }
    public static void register(IEventBus modBus) {
        modBus.addListener(WayfinderNetwork::register);
        NeoForge.EVENT_BUS.addListener((ServerAboutToStartEvent event) -> SERVERS.put(event.getServer(),
                new State(WayfinderSettings.load(ConfigLocations.ownedDirectory(FMLPaths.CONFIGDIR.get())))));
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> event.getDispatcher().register(
                Commands.literal("skyweavecompat").then(Commands.literal("wayfinder").executes(context -> {
                    start(context.getSource().getPlayerOrException()); return 1;
                }).then(Commands.literal("cancel").executes(context -> {
                    var player = context.getSource().getPlayerOrException(); var state = SERVERS.get(player.server);
                    if (state != null && state.jobs.containsKey(player.getUUID())) finish(state,state.jobs.get(player.getUUID()),"cancelled");
                    return 1;
                })))));
        NeoForge.EVENT_BUS.addListener(Wayfinder::tick);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.level.LevelEvent.Unload event) -> {
            if (!(event.getLevel() instanceof ServerLevel level)) return;
            var state = SERVERS.get(level.getServer());
            if (state != null) for (var job : List.copyOf(state.jobs.values()))
                if (job.level == level) finish(state,job,"cancelled");
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> {
            var state = SERVERS.remove(event.getServer());
            if (state != null) {
                for (var job : List.copyOf(state.jobs.values())) finish(state,job,"cancelled");
                state.worker.shutdownNow();
            }
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                var state = SERVERS.get(player.server);
                if (state != null) {
                    state.warps.remove(player.getUUID());
                    var job = state.jobs.get(player.getUUID()); if (job != null) finish(state,job,"cancelled");
                }
            }
        });
    }
    private static boolean permitted(ServerPlayer player, State state) { return !state.settings.operatorsOnly() || player.hasPermissions(2); }
    private static boolean usable(ServerPlayer player) { return !player.isPassenger() && !player.isVehicle() && !player.isFallFlying() && !player.isSpectator() && player.isAlive(); }
    private static void message(State state, ServerPlayer player, String key) {
        if (state.settings.debugMessages())
            player.displayClientMessage(LocatorText.text("wayfinder",key),true);
    }
    public static void start(ServerPlayer player) {
        var state = SERVERS.get(player.server);
        if (state == null || player.server.getPlayerList().getPlayer(player.getUUID()) != player) return;
        if (!permitted(player,state)) { message(state,player,"denied"); return; }
        if (!usable(player)) { message(state,player,"unavailable"); return; }
        var old = state.jobs.get(player.getUUID());
        if (old != null) { finish(state,old,"cancelled"); return; }
        if (state.clock - state.warps.getOrDefault(player.getUUID(),Long.MIN_VALUE / 2) < state.settings.cooldownTicks()) {
            message(state,player,"cooldown"); return;
        }
        if (state.jobs.size() >= state.settings.concurrentRequests() || state.requests >= 16) { message(state,player,"busy"); return; }
        Job job;
        try { job = new Job(player,state.clock); }
        catch (IllegalArgumentException invalid) { message(state,player,"miss"); return; }
        catch (RuntimeException unavailable) { message(state,player,"failed"); return; }
        state.jobs.put(player.getUUID(),job);
        message(state,player,"pending");
        if (job.frames == null) query(state,job);
        else { job.phase = Phase.FRAMES; frames(state,job); }
    }
    private static void query(State state, Job job) {
        if (job.player.connection.hasChannel(WayfinderPacket.TYPE)) {
            job.phase = Phase.CLIENT;
            PacketDistributor.sendToPlayer(job.player,new WayfinderPacket(WayfinderPacket.QUERY,job.id,
                    job.ray.x(),job.ray.y(),job.ray.z(),job.ray.dx(),job.ray.dy(),job.ray.dz(),
                    job.level.dimension().location().toString(),state.settings.stepsPerTick(),state.settings.requestTimeoutTicks(),state.settings.blockedBlocks()));
        } else job.phase = Phase.NATIVE;
    }
    private static boolean frameBlocked(Job job) {
        if (job.frames == null) return false;
        double targetDistance = job.observed.subtract(job.ray.x(),job.ray.y(),job.ray.z())
                .dot(new Vec3(job.ray.dx(),job.ray.dy(),job.ray.dz()));
        return job.frames.storage(job.target) || job.frames.firstDistance() <= targetDistance + 1e-6;
    }
    private static void frames(State state, Job job) {
        try {
            if (!job.frames.advance(state.settings.stepsPerTick())) return;
            if (job.phase == Phase.FRAMES) {
                if (job.frames.firstDistance() == 0) finish(state,job,"construct");
                else query(state,job);
            } else if (frameBlocked(job)) finish(state,job,"construct");
            else land(state,job);
        } catch (java.util.ConcurrentModificationException changed) { finish(state,job,"cancelled"); }
        catch (RuntimeException unavailable) { finish(state,job,"failed"); }
    }
    public static void receive(ServerPlayer player, WayfinderPacket packet) {
        if (packet.operation() == WayfinderPacket.START) { start(player); return; }
        var state = SERVERS.get(player.server); if (state == null) return;
        var job = state.jobs.get(player.getUUID());
        if (job == null || job.player != player || !job.id.equals(packet.request()) || !job.running) return;
        if (!valid(state,job)) { finish(state,job,"cancelled"); return; }
        switch (packet.operation()) {
            case WayfinderPacket.CANCEL -> finish(state,job,"cancelled");
            case WayfinderPacket.ALIVE -> { if (job.phase == Phase.CLIENT) job.touched = state.clock; }
            case WayfinderPacket.MISSING -> finish(state,job,"missing");
            case WayfinderPacket.MISS -> finish(state,job,"miss");
            case WayfinderPacket.FAILED -> finish(state,job,"failed");
            case WayfinderPacket.INCOMPLETE -> finish(state,job,"incomplete");
            case WayfinderPacket.CONSTRUCT -> finish(state,job,"construct");
            case WayfinderPacket.HIT -> {
                if (job.phase != Phase.CLIENT) return;
                if (!Double.isFinite(packet.x()) || !Double.isFinite(packet.y()) || !Double.isFinite(packet.z())) {
                    finish(state,job,"invalid"); return;
                }
                var target = BlockPos.containing(packet.x(),packet.y(),packet.z());
                if (!net.minecraft.world.level.Level.isInSpawnableBounds(target)
                        || target.getY() < job.level.getMinBuildHeight() || target.getY() >= job.level.getMaxBuildHeight()
                        || !job.level.getWorldBorder().isWithinBounds(target)) { finish(state,job,"invalid"); return; }
                var intersection = job.ray.intersect(target.getX(),target.getY(),target.getZ(),target.getX()+1.,target.getY()+1.,target.getZ()+1.);
                if (intersection == null) { finish(state,job,"invalid"); return; }
                var hit = job.ray.at(intersection.enter());
                if (new Vec3(packet.x(),packet.y(),packet.z()).distanceToSqr(new Vec3(hit.x(),hit.y(),hit.z())) > 3.01) {
                    finish(state,job,"invalid"); return;
                }
                job.target = target; job.observed = new Vec3(packet.x(),packet.y(),packet.z()); job.phase = Phase.VERIFY_NEAR;
            }
            default -> {} // Client cannot issue QUERY or create a server-authorized ray.
        }
    }
    private static boolean valid(State state, Job job) {
        var player = job.player;
        if (player.server.getPlayerList().getPlayer(player.getUUID()) != player || player.serverLevel() != job.level
                || player.server.getLevel(job.level.dimension()) != job.level
                || !permitted(player,state) || !usable(player)) return false;
        var eye = player.getEyePosition(); var view = player.getViewVector(1);
        return eye.distanceToSqr(new Vec3(job.ray.x(),job.ray.y(),job.ray.z())) <= .25
                && job.ray.sameDirection(view.x,view.y,view.z);
    }
    private static void tick(ServerTickEvent.Post event) {
        var state = SERVERS.get(event.getServer()); if (state == null) return;
        state.clock++;
        for (var job : List.copyOf(state.jobs.values())) {
            if (!valid(state,job)) { finish(state,job,"cancelled"); continue; }
            if (state.clock - job.touched > (job.phase == Phase.DESTINATION ? state.settings.requestTimeoutTicks() : 100)) {
                finish(state,job,"incomplete"); continue;
            }
            if (state.clock %40 == 0) message(state,job.player,"pending");
            if (job.phase == Phase.CLIENT || job.phase == Phase.DESTINATION) continue;
            job.touched = state.clock;
            if (job.phase == Phase.FRAMES || job.phase == Phase.FINAL_FRAMES) { frames(state,job); continue; }
            for (int step = 0; step < state.settings.stepsPerTick() && job.running; step++) {
                if (job.cursor.complete()) { finish(state,job,"miss"); break; }
                var pos = new BlockPos(job.cursor.x(),job.cursor.y(),job.cursor.z());
                if (job.frames != null && job.frames.storage(pos)) { finish(state,job,"construct"); break; }
                boolean loaded = job.level.getChunkSource().getChunkNow(pos.getX() >> 4,pos.getZ() >> 4) != null;
                if (!loaded || job.phase == Phase.VERIFY_NEAR && job.cursor.distance() >
                        Math.sqrt(job.observed.distanceToSqr(new Vec3(job.ray.x(),job.ray.y(),job.ray.z()))) + 2) {
                    if (job.phase == Phase.NATIVE) finish(state,job,"client_required");
                    else load(state,job);
                    break;
                }
                var hit = WayfinderGeometry.hit(job.level,job.player,job.ray,job.cursor,state.settings.blockedBlocks());
                if (hit != null) {
                    if (job.phase == Phase.VERIFY_NEAR && !hit.getBlockPos().equals(job.target)) { finish(state,job,"obstructed"); break; }
                    if (job.phase == Phase.NATIVE) {
                        job.target = hit.getBlockPos(); job.observed = hit.getLocation();
                    }
                    load(state,job); break;
                }
                job.cursor.advance();
            }
        }
    }
    private static void load(State state, Job job) {
        if (frameBlocked(job)) { finish(state,job,"construct"); return; }
        var footprint = WayfinderGeometry.footprint(job.player,job.target);
        if (footprint == null) { finish(state,job,"unsafe"); return; }
        if (job.frames != null) for (int x = footprint.minX(); x <= footprint.maxX(); x++) for (int z = footprint.minZ(); z <= footprint.maxZ(); z++)
            if (job.frames.storage(new ChunkPos(x,z))) { finish(state,job,"construct"); return; }
        int needed = footprint.chunks();
        if (state.requests + needed > state.settings.concurrentRequests() * 4) { finish(state,job,"busy"); return; }
        job.phase = Phase.DESTINATION; job.touched = state.clock;
        var futures = new ArrayList<CompletableFuture<?>>();
        try {
            for (int x = footprint.minX(); x <= footprint.maxX(); x++)
                for (int z = footprint.minZ(); z <= footprint.maxZ(); z++) {
                    var pos = new ChunkPos(x,z);
                    ((StructureSearchCacheAccess)job.level.getChunkSource()).skyweave$searchTickets().addTicket(TICKET,pos,TICKET_LEVEL,job.id);
                    job.leases.add(pos);
                    var future = CompletableFuture.supplyAsync(() -> job.running
                            ? job.level.getChunkSource().getChunkFuture(pos.x,pos.z,ChunkStatus.FULL,true)
                            : CompletableFuture.<ChunkResult<net.minecraft.world.level.chunk.ChunkAccess>>completedFuture(null),state.worker)
                            .thenCompose(value -> value).thenAccept(chunk -> {
                                if (chunk != null && !chunk.isSuccess()) throw new IllegalStateException(chunk.getError());
                            });
                    state.requests++;
                    future.whenComplete((ignored,error) -> job.player.server.execute(() -> state.requests--));
                    futures.add(future);
                }
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).whenComplete((ignored,error) -> job.player.server.execute(() -> {
                if (!job.running) return;
                if (error != null) { finish(state,job,"failed"); return; }
                if (!valid(state,job)) { finish(state,job,"cancelled"); return; }
                try {
                    job.frames = WayfinderFrames.open(job.level,job.ray);
                    if (job.frames == null) land(state,job);
                    else { job.phase = Phase.FINAL_FRAMES; job.touched = state.clock; }
                } catch (RuntimeException unavailable) { finish(state,job,"failed"); }
            }));
        } catch (RuntimeException error) { finish(state,job,"failed"); }
    }
    private static void land(State state, Job job) {
        var footprint = WayfinderGeometry.footprint(job.player,job.target);
        if (footprint == null) { finish(state,job,"unsafe"); return; }
        for (int x = footprint.minX(); x <= footprint.maxX(); x++) for (int z = footprint.minZ(); z <= footprint.maxZ(); z++)
            if (!job.leases.contains(new ChunkPos(x,z))) { finish(state,job,"unsafe"); return; }
        var actual = WayfinderGeometry.surface(job.level,job.player,job.ray,job.target,job.cursor.end());
        var position = actual == null || actual.isInside() || actual.getDirection() == net.minecraft.core.Direction.DOWN
                ? null : WayfinderGeometry.landing(job.level,job.player,job.target,
                actual.getLocation(),state.settings.blockedBlocks(),state.settings.placementLookupHeight());
        if (position == null) { finish(state,job,"unsafe"); return; }
        var hook = net.neoforged.neoforge.event.EventHooks.onEntityTeleportCommand(job.player,position.x,position.y,position.z);
        if (hook.isCanceled() || hook.getTargetX() != position.x || hook.getTargetY() != position.y || hook.getTargetZ() != position.z) {
            finish(state,job,"denied"); return;
        }
        if (job.player.teleportTo(job.level,position.x,position.y,position.z,Set.of(),job.player.getYRot(),job.player.getXRot())) {
            job.player.setDeltaMovement(Vec3.ZERO); job.player.setOnGround(true); job.player.fallDistance = 0;
            if (state.settings.cooldownTicks() > 0) state.warps.put(job.player.getUUID(),state.clock);
            finish(state,job,"success");
        } else finish(state,job,"failed");
    }
    private static void finish(State state, Job job, String outcome) {
        if (!job.running) return;
        job.running = false; state.jobs.remove(job.player.getUUID(),job);
        job.frames = null;
        var tickets = ((StructureSearchCacheAccess)job.level.getChunkSource()).skyweave$searchTickets();
        for (var pos : job.leases) tickets.removeTicket(TICKET,pos,TICKET_LEVEL,job.id);
        job.leases.clear();
        if (job.player.server.getPlayerList().getPlayer(job.player.getUUID()) == job.player) {
            if (job.player.connection.hasChannel(WayfinderPacket.TYPE))
                PacketDistributor.sendToPlayer(job.player,WayfinderPacket.signal(WayfinderPacket.CANCEL,job.id));
            message(state,job.player,outcome);
        }
    }
}
