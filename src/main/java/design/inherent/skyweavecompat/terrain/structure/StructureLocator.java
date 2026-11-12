package design.inherent.skyweavecompat.terrain.structure;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import design.inherent.skyweavecompat.mixin.StructureSearchCacheAccess;
import design.inherent.skyweavecompat.mixin.StructureSearchChunksAccess;
import design.inherent.skyweavecompat.terrain.LocatorText;
import design.inherent.skyweavecompat.configuration.SettingsPolicy;
import design.inherent.skyweavecompat.configuration.SettingsReload;
import design.inherent.skyweavecompat.configuration.ConfigFile;
import design.inherent.skyweavecompat.configuration.ConfigLocations;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import design.inherent.skyweavecompat.terrain.LocatorOwner;
import design.inherent.skyweavecompat.placement.ArchipelagoVillagePlacement;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.*;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.visitors.CollectFields;
import net.minecraft.nbt.visitors.FieldSelector;
import net.minecraft.network.chat.*;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.*;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.*;
import java.util.concurrent.*;

/** Resumable discovery of actual native starts; never calls synchronous locate or waits on a server tick. */
public final class StructureLocator {
    private static final TicketType<UUID> TICKET = TicketType.create("skyweave_structure_search", UUID::compareTo);
    private static final int TICKET_LEVEL = ChunkLevel.byStatus(ChunkStatus.STRUCTURE_STARTS);
    private static final Map<MinecraftServer, State> servers = new IdentityHashMap<>();
    private static final class State {
        final SettingsPolicy<SearchSettings> policy;
        State(MinecraftServer server, SearchSettings settings) { policy = new SettingsPolicy<>(settings, () -> SettingsReload.requireThread(server)); }
        final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(4), task -> {
                    var thread = new Thread(task, "Skyweave structure requests"); thread.setDaemon(true); return thread;
                });
        // Enumeration can wait on bounded delivery; native admission must not queue behind its producer.
        final ThreadPoolExecutor requests = new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(2),task -> {
                    var thread=new Thread(task,"Skyweave structure admission");thread.setDaemon(true);return thread;
                });
        Job job;
        int nativePending, metadataPending;
    }
    private record Target(Holder<Structure> structure, List<RandomSpreadStructurePlacement> placements) {}
    private record Selection(List<Target> targets, int unplaced, int unsupported) {}
    private record Grid(int spacing, int separation, RandomSpreadType spread, int salt, int customIdentity) {}
    private record Candidate(ChunkPos position, long distance) {}
    private record Enumeration(boolean limited) {}
    private enum Reservation { NEW, DUPLICATE, LIMIT }
    private record Ownership(StructureDistribution distribution, StructureFamily family) {}
    private record Match(String id, net.minecraft.core.BlockPos position, long distance) {}
    private static final Comparator<Match> ORDER = Comparator.comparingLong(Match::distance).thenComparing(Match::id)
            .thenComparingLong(match -> match.position().asLong());
    private static final class Job {
        final UUID id = UUID.randomUUID();
        final LocatorOwner owner;
        final ServerLevel level;
        final int x, z, radius;
        final SearchSettings limits;
        final List<Target> targets;
        final Set<String> targetIds;
        final List<RandomSpreadStructurePlacement> placements;
        final long locateOffset;
        long sourceShift;
        Set<String> unboundedTargets = Set.of();
        final Set<ChunkPos> leases = new HashSet<>();
        final Set<String> seen = new HashSet<>();
        final Map<net.minecraft.world.level.levelgen.structure.StructureStart, String> identities = new LinkedHashMap<>(128, .75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<net.minecraft.world.level.levelgen.structure.StructureStart, String> entry) {
                return size() > 256;
            }
        };
        final ArrayList<Match> matches = new ArrayList<>();
        final long deadline;
        volatile boolean running = true;
        final ArrayBlockingQueue<Candidate> incoming = new ArrayBlockingQueue<>(64);
        final ArrayDeque<ChunkHolder> loaded = new ArrayDeque<>();
        volatile boolean boundsReady, enumerationDone;
        Future<?> enumerationTask;
        boolean boundsReported;
        int verified, rejected, pending, savedChunks, savedRegions;
        volatile int clusters;
        volatile long enumerationNanos;
        long metadataNanos, nativeNanos;
        boolean savedDone, savedBusy;
        List<ChunkPos> regions;
        int nextRegion;
        final ArrayDeque<ChunkPos> stored = new ArrayDeque<>();
        final Set<ChunkPos> scheduled = ConcurrentHashMap.newKeySet();
        final ArrayDeque<Candidate> ready = new ArrayDeque<>();
        volatile boolean limited;
        String outcome = "progress", failureReason = "";
        long nextProgress;
        Job(CommandSourceStack source, int radius, List<Target> targets, SearchSettings limits) {
            owner = new LocatorOwner(source); level = source.getLevel(); this.radius = radius; this.targets = targets;
            targetIds = targets.stream().map(t -> t.structure().getRegisteredName()).collect(java.util.stream.Collectors.toUnmodifiableSet());
            placements = targets.stream().flatMap(t -> t.placements().stream()).distinct().toList();
            locateOffset = Math.max(8, placements.stream().map(p -> p.getLocatePos(new ChunkPos(0, 0)))
                    .mapToLong(p -> Math.max(Math.abs((long)p.getX()), Math.abs((long)p.getZ()))).max().orElse(8));
            x = net.minecraft.util.Mth.floor(source.getPosition().x); z = net.minecraft.util.Mth.floor(source.getPosition().z);
            this.limits = limits;
            long now = System.nanoTime(); deadline = now + TimeUnit.SECONDS.toNanos(limits.timeLimitSeconds());
            nextProgress = now + TimeUnit.SECONDS.toNanos(2);
        }
    }
    public static void register() {
        var locator = new StructureLocator();
        NeoForge.EVENT_BUS.addListener((ServerAboutToStartEvent event) -> servers.put(event.getServer(),
                new State(event.getServer(), SearchSettings.load(ConfigLocations.ownedDirectory(FMLPaths.CONFIGDIR.get())))));
        SettingsReload.register("search", StructureLocator::reloadPolicy);
        NeoForge.EVENT_BUS.addListener(locator::commands);
        NeoForge.EVENT_BUS.addListener(locator::tick);
        NeoForge.EVENT_BUS.addListener((LevelEvent.Unload event) -> {
            if (!(event.getLevel() instanceof ServerLevel level)) return;
            var state = servers.get(level.getServer());
            if (state != null && state.job != null && state.job.level == level) locator.stop(state.job, "cancelled", false);
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> {
            var state = servers.remove(event.getServer());
            if (state != null) {
                if (state.job != null) locator.stop(state.job,"cancelled",false);
                state.policy.close();
                state.worker.shutdownNow();state.requests.shutdownNow();
            }
        });
    }
    public static SettingsPolicy.Snapshot<SearchSettings> readPolicy(CommandSourceStack source) {
        SettingsReload.requireThread(source.getServer());
        var state = servers.get(source.getServer());
        if (state == null) throw new IllegalStateException("Structure search is unavailable in this server context");
        return state.policy.read(() -> source.hasPermission(2));
    }
    public static SettingsPolicy.Snapshot<SearchSettings> applyPolicy(CommandSourceStack source,
            SettingsPolicy.Revision expected, SearchSettings candidate) {
        SettingsReload.requireThread(source.getServer());
        var state = servers.get(source.getServer());
        if (state == null) throw new IllegalStateException("Structure search is unavailable in this server context");
        return state.policy.prepare(expected,candidate,() -> source.hasPermission(2)).apply(() -> source.hasPermission(2));
    }
    private static boolean reloadPolicy(CommandSourceStack source) throws java.io.IOException {
        var before = readPolicy(source);
        var file = ConfigLocations.file(FMLPaths.CONFIGDIR.get(), SearchSettings.FILE);
        var candidate = SearchSettings.parse(ConfigFile.read(file));
        return applyPolicy(source,before.revision(),candidate).revision() != before.revision();
    }
    private void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("skyweavecompat").requires(source -> source.hasPermission(2))
                .then(Commands.literal("structures").requires(source -> source.hasPermission(2))
                    .executes(ctx -> page(ctx.getSource(), 1))
                    .then(Commands.literal("search").then(Commands.argument("radius", IntegerArgumentType.integer(1, 131072))
                        .then(Commands.argument("target", StringArgumentType.greedyString()).suggests((ctx, builder) -> {
                            for (var value : suggestions(ctx.getSource())) if (value.startsWith(builder.getRemaining())) builder.suggest(value);
                            return builder.buildFuture();
                        }).executes(ctx -> start(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius"), StringArgumentType.getString(ctx, "target"))))))
                    .then(Commands.literal("status").executes(ctx -> status(ctx.getSource())))
                    .then(Commands.literal("page").executes(ctx -> page(ctx.getSource(), 1))
                        .then(Commands.argument("number", IntegerArgumentType.integer(1, 7))
                            .executes(ctx -> page(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "number")))))
                    .then(Commands.literal("cancel").executes(ctx -> {
                        var job = owned(ctx.getSource()); if (job == null) return fail(ctx.getSource(), "missing");
                        stop(job, "cancelled", true); return 1;
                    }))));
    }
    private static List<String> suggestions(CommandSourceStack source) {
        var result = new ArrayList<String>();
        for (var family : StructureFamily.values()) result.add("family=" + family.key);
        var registry = source.registryAccess().registryOrThrow(Registries.STRUCTURE);
        registry.keySet().stream().sorted().forEach(id -> result.add("structure=" + id));
        registry.getTagNames().map(tag -> "structure=#" + tag.location()).sorted().forEach(result::add);
        return result;
    }
    private static Selection targets(CommandSourceStack source, String raw) {
        String[] selector = raw.strip().split("=", -1);
        if (selector.length != 2 || selector[1].isBlank() || selector[1].contains(" ")) throw new IllegalArgumentException("Use one family=… or structure=… selector");
        var level = source.getLevel(); var state = level.getChunkSource().getGeneratorState();
        var distribution = state instanceof StructurePlacementState configured ? configured.skyweave$structureDistribution() : null;
        var registry = source.registryAccess().registryOrThrow(Registries.STRUCTURE);
        boolean aggregate = selector[0].equals("family") || selector[1].startsWith("#");
        List<Holder<Structure>> selected;
        if (selector[0].equals("family")) {
            var family = Arrays.stream(StructureFamily.values()).filter(value -> value.key.equals(selector[1])).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown structure family"));
            if (distribution == null) throw new IllegalArgumentException("Structure family policy is disabled");
            selected = registry.holders().filter(holder -> distribution.family(holder.key().location()) == family)
                    .map(holder -> (Holder<Structure>)holder).toList();
        } else if (selector[0].equals("structure")) {
            boolean tag = selector[1].startsWith("#");
            var id = ResourceLocation.tryParse(tag ? selector[1].substring(1) : selector[1]);
            if (id == null) throw new IllegalArgumentException("Invalid structure ID");
            if (tag) selected = registry.getTag(TagKey.create(Registries.STRUCTURE, id))
                    .map(values -> values.stream().toList()).orElseThrow(() -> new IllegalArgumentException("Unknown structure tag"));
            else selected = List.of(registry.getHolder(ResourceKey.create(Registries.STRUCTURE, id))
                    .orElseThrow(() -> new IllegalArgumentException("Unknown structure ID")));
        } else throw new IllegalArgumentException("Use family=… or structure=…");
        if (selected.isEmpty()) throw new IllegalArgumentException("The selector has no structures");
        var targets = new ArrayList<Target>(); int unplaced = 0, unsupported = 0;
        for (var holder : selected) {
            var placements = state.getPlacementsForStructure(holder);
            if (placements.isEmpty()) { unplaced++; targets.add(new Target(holder, List.of())); continue; }
            boolean custom = placements.stream().anyMatch(placement -> !(placement instanceof RandomSpreadStructurePlacement));
            if (custom && !aggregate)
                throw new IllegalArgumentException("Unsupported or unavailable placement: " + holder.getRegisteredName());
            if (custom) unsupported++;
            var supported = placements.stream().filter(RandomSpreadStructurePlacement.class::isInstance).map(RandomSpreadStructurePlacement.class::cast).distinct().toList();
            if (!supported.isEmpty()) targets.add(new Target(holder, supported));
        }
        if (targets.isEmpty()) throw new IllegalArgumentException("No selected structures have supported placements in this world");
        return new Selection(List.copyOf(targets), unplaced, unsupported);
    }
    private int start(CommandSourceStack source, int radius, String selector) {
        try {
            if (!(source.getLevel().getChunkSource().getGenerator() instanceof design.inherent.skyweavecompat.terrain.GeologicalGenerator generator)
                    || !generator.skyweave$isEnabled()) return fail(source, "unsupported");
            var state = servers.get(source.getServer());
            if (state == null) return fail(source,"unsupported");
            if (state.job != null && state.job.running) return fail(source, "busy");
            var selection = targets(source, selector);
            var job = new Job(source, radius, selection.targets(), state.policy.current());
            job.limited = selection.unsupported() > 0;
            if (Math.abs((long)job.x) > 29999983 || Math.abs((long)job.z) > 29999983)
                throw new IllegalArgumentException("Search origin is outside Minecraft's coordinate bounds");
            state.job = job;
            source.sendSuccess(() -> text("started", radius).copy().append(" ").append(action("cancel", "cancel")), false);
            if (selection.unplaced() > 0) source.sendSuccess(() -> text("unplaced", selection.unplaced())
                    .copy().withStyle(ChatFormatting.YELLOW), false);
            if (selection.unsupported() > 0) source.sendSuccess(() -> text("unsupported_entries", selection.unsupported())
                    .copy().withStyle(ChatFormatting.YELLOW), false);
            // Loaded proto/full chunks may contain unsaved starts anywhere, including old placements.
            var chunks = (StructureSearchChunksAccess)source.getLevel().getChunkSource().chunkMap;
            var holders = new HashSet<ChunkHolder>();
            int visits = 0, limit = job.limits.candidateLimit();
            loadedSnapshot:
            for (var sourceHolders : List.of(chunks.skyweave$searchChunks(),
                    chunks.skyweave$searchUpdatingChunks().values(), chunks.skyweave$searchUnloadingChunks().values())) {
                for (var holder : sourceHolders) {
                    if (++visits > 3 * limit) { job.limited = true; break loadedSnapshot; }
                    if (holders.contains(holder)) continue;
                    if (holders.size() == limit) { job.limited = true; break loadedSnapshot; }
                    holders.add(holder); job.loaded.addLast(holder);
                }
            }
            job.enumerationTask=state.worker.submit(() -> {
                try {
                    long began = System.nanoTime();
                    var result = enumerate(job);
                    job.enumerationNanos = System.nanoTime() - began;
                    source.getServer().execute(() -> {
                        if (!job.running || state.job != job) return;
                        job.enumerationDone=true;if(result.limited())job.limited=true;
                    });
                } catch (Throwable failure) { source.getServer().execute(() -> failed(job, failure)); }
            });
            return 1;
        } catch (RejectedExecutionException busy) {
            var state = servers.get(source.getServer()); if (state != null && state.job != null) stop(state.job, "cancelled", false);
            return fail(source, "busy");
        } catch (IllegalArgumentException failure) { source.sendFailure(text("invalid", failure.getMessage())); return 0; }
    }
    private static Enumeration enumerate(Job job) {
        var bounds = new StructureSearchBounds(job.level, () -> job.running && System.nanoTime() < job.deadline);
        var unbounded = new TreeSet<String>();
        var settings = job.level.getChunkSource().getGenerator()
                instanceof org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator sky
                ? sky.resolvedSkyIslandSettings() : null;
        for (var target : job.targets) {
            var measured = bounds.displacement(target.structure().value());
            if (measured.isEmpty()) { unbounded.add(target.structure().getRegisteredName()); continue; }
            long relocation = 0;
            if (target.placements().isEmpty()) unbounded.add(target.structure().getRegisteredName());
            for (var placement : target.placements()) {
                if (placement instanceof ArchipelagoStructurePlacement managed && managed.family() != StructureFamily.VILLAGES)
                    relocation = Math.max(relocation, managed.distribution().maximumAnchorShift());
                else if (placement instanceof ArchipelagoVillagePlacement sky)
                    relocation = Math.max(relocation, sky.maximumNativeShift());
                else if (!(placement instanceof ArchipelagoStructurePlacement) && placement.getClass() != RandomSpreadStructurePlacement.class)
                    unbounded.add(target.structure().getRegisteredName());
                else if (!(placement instanceof ArchipelagoStructurePlacement)
                        && (settings == null || settings.advanced().customStructureRulesEnabled()
                        && !settings.advanced().structureWhitelist().isWhitelisted(target.structure().unwrapKey().orElseThrow().location()))
                        )
                    unbounded.add(target.structure().getRegisteredName());
            }
            job.sourceShift = Math.max(job.sourceShift, Math.addExact(measured.getAsLong(), relocation));
        }
        job.unboundedTargets = Set.copyOf(unbounded);
        job.boundsReady=true;
        var grids = new LinkedHashMap<Grid, List<RandomSpreadStructurePlacement>>();
        var owned = new LinkedHashMap<Ownership, List<RandomSpreadStructurePlacement>>();
        var ownedTargets = new HashMap<Ownership, Set<ResourceLocation>>();
        var villages = new LinkedHashSet<ArchipelagoVillagePlacement>();
        var customGrids = new IdentityHashMap<RandomSpreadStructurePlacement, Integer>();
        for (var target : job.targets) for (var placement : target.placements()) {
            if (placement instanceof ArchipelagoStructurePlacement managed) {
                var ownership = new Ownership(managed.distribution(), managed.family());
                owned.computeIfAbsent(ownership, ignored -> new ArrayList<>()).add(placement);
                ownedTargets.computeIfAbsent(ownership, ignored -> new HashSet<>()).add(target.structure().unwrapKey().orElseThrow().location());
                continue;
            }
            if (placement instanceof ArchipelagoVillagePlacement village) {
                villages.add(village); continue;
            }
            // Only exact vanilla placements share a grid contract. Unknown subclasses keep their own implementation/state.
            int custom = placement.getClass() == RandomSpreadStructurePlacement.class ? 0
                    : customGrids.computeIfAbsent(placement, ignored -> customGrids.size() + 1);
            var key = new Grid(placement.spacing(), placement.separation(), placement.spreadType(), ((design.inherent.skyweavecompat.mixin.PlacementSaltAccessor)placement).skyweave$salt(), custom);
            grids.computeIfAbsent(key, ignored -> new ArrayList<>()).add(placement);
        }
        grids.replaceAll((key, placements) -> placements.stream().distinct().toList());
        owned.replaceAll((key, placements) -> placements.stream().distinct().toList());
        long seed = job.level.getSeed(); boolean limited = !unbounded.isEmpty();
        var distributions = owned.keySet().stream().map(Ownership::distribution).distinct().toList();
        for (var distribution : distributions) {
            var cells = distribution.searchOwners(job.x, job.z, job.radius, locateOffset(job) + 16);
            while (!cells.complete()) {
                if (!job.running || System.nanoTime() >= job.deadline) return new Enumeration(true);
                var cell = cells.next(); job.clusters++;
                var owner = new StructureSelection.Owner(cell.x(), cell.z());
                for (var entry : owned.entrySet()) {
                    if (entry.getKey().distribution() != distribution) continue;
                    for (var site : distribution.searchSites(owner, entry.getKey().family())) {
                        var pos = new ChunkPos(site.chunkX(), site.chunkZ());
                        if (!StructureSearchBounds.withinWorld(pos.x, pos.z)) continue;
                        if (!distribution.possibleNewStart(entry.getKey().family(), pos.x, pos.z)) continue;
                        if (!distribution.possibleSearchVariant(owner, entry.getKey().family(), site, ownedTargets.get(entry.getKey()))) continue;
                        var candidate = candidate(job, pos, entry.getValue());
                        if (candidate.distance() > (long)job.radius * job.radius) continue;
                        if(!publish(job,candidate))return new Enumeration(true);
                    }
                }
            }
        }
        for (var placement : villages) {
            var cells = placement.searchOwners(job.x, job.z, job.radius, locateOffset(job) + 16);
            while (!cells.complete()) {
                if (!job.running || System.nanoTime() >= job.deadline) return new Enumeration(true);
                var cell = cells.next(); job.clusters++;
                for (var site : placement.searchSites(cell.x(), cell.z(), job.x, job.z, job.radius, locateOffset(job) + 16)) {
                    var pos = new ChunkPos(site.chunkX(), site.chunkZ());
                    if (!StructureSearchBounds.withinWorld(pos.x, pos.z)) continue;
                    var candidate = candidate(job, pos, List.of(placement));
                    if (candidate.distance() > (long)job.radius * job.radius) continue;
                    if(!publish(job,candidate))return new Enumeration(true);
                }
            }
        }
        // Rings visit nearby source regions first; only a fully exhausted domain claims completeness.
        for (int ring = 0; ; ring++) {
            boolean any = false;
            for (var grid : grids.entrySet()) {
                int spacing = grid.getKey().spacing();
                long reach = Math.ceilDiv(job.radius + locateOffset(job) + 16, spacing * 16L) + 1;
                if (ring > reach) continue;
                any = true;
                int cx = Math.floorDiv(Math.floorDiv(job.x, 16), spacing), cz = Math.floorDiv(Math.floorDiv(job.z, 16), spacing);
                for (int dx = -ring; dx <= ring; dx++) for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                    if (!job.running || System.nanoTime() >= job.deadline) return new Enumeration(true);
                    var placement = grid.getValue().getFirst();
                    var pos = placement.getPotentialStructureChunk(seed, (cx + dx) * spacing, (cz + dz) * spacing);
                    if (!StructureSearchBounds.withinWorld(pos.x, pos.z)) continue;
                    long nearest = candidate(job, pos, grid.getValue()).distance();
                    if (nearest > (long)job.radius * job.radius) continue;
                    if(!publish(job,new Candidate(pos,nearest)))return new Enumeration(true);
                }
            }
            if (!any) break;
        }
        return new Enumeration(limited);
    }
    private static Reservation reserve(Job job,ChunkPos position) {
        synchronized(job.scheduled) {
            if(job.scheduled.contains(position))return Reservation.DUPLICATE;
            if(job.scheduled.size()>=job.limits.candidateLimit()){job.limited=true;return Reservation.LIMIT;}
            job.scheduled.add(position);return Reservation.NEW;
        }
    }
    private static boolean publish(Job job,Candidate candidate) {
        var reservation=reserve(job,candidate.position());
        if(reservation!=Reservation.NEW)return reservation==Reservation.DUPLICATE;
        while(job.running && System.nanoTime()<job.deadline)try {
            if(job.incoming.offer(candidate,50,TimeUnit.MILLISECONDS)) {
                if(!job.running){job.incoming.remove(candidate);return false;}
                return true;
            }
        } catch(InterruptedException stopped) {Thread.currentThread().interrupt();return false;}
        return false;
    }
    private static long locateOffset(Job job) {
        return job.locateOffset + job.sourceShift;
    }
    private static Candidate candidate(Job job, ChunkPos pos, List<RandomSpreadStructurePlacement> placements) {
        // A type can have multiple source grids/locate offsets. The representative
        // offset is independent of which source grid delivered its alias.
        long distance = job.placements.stream().mapToLong(p -> minimumDistance(job, p.getLocatePos(pos)))
                .min().orElseGet(() -> minimumDistance(job, new net.minecraft.core.BlockPos(pos.getMiddleBlockX(), 0, pos.getMiddleBlockZ())));
        if (job.targets.stream().anyMatch(t -> t.placements().isEmpty()))
            distance = Math.min(distance, minimumDistance(job, new net.minecraft.core.BlockPos(pos.getMiddleBlockX(), 0, pos.getMiddleBlockZ())));
        return new Candidate(pos,distance);
    }
    private static boolean inRange(Job job, ChunkPos pos) {
        return candidate(job, pos, List.of()).distance() <= (long)job.radius * job.radius;
    }
    private void tick(ServerTickEvent.Post event) {
        var state = servers.get(event.getServer()); var job = state == null ? null : state.job;
        if (job == null || !job.running) return;
        if (!validOwner(job)) {
            job.owner.success(job.level.getServer(),null,() -> text("cancelled",job.verified+job.rejected,job.seen.size()));
            stop(job,"cancelled",false);return;
        }
        if (System.nanoTime() >= job.deadline) { stop(job, "partial", true); return; }
        long until = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(job.limits.tickBudgetMillis());
        try {
            while(!job.loaded.isEmpty() && System.nanoTime()<until) {
                var chunk=job.loaded.removeFirst().getLatestChunk();
                if(chunk!=null)inspect(job,candidate(job,chunk.getPos(),List.of()),chunk,true);
            }
            if(job.boundsReady && !job.boundsReported) {
                job.boundsReported=true;
                if(!job.unboundedTargets.isEmpty())reply(job,() -> text("unsupported_layouts",job.unboundedTargets.size())
                        .copy().withStyle(style -> style.withColor(ChatFormatting.YELLOW).withHoverEvent(new HoverEvent(
                                HoverEvent.Action.SHOW_TEXT,Component.literal(String.join(", ",job.unboundedTargets.stream().sorted().limit(8).toList()))))));
            }
            // Reserve 32 of the 64 ready slots for the one outstanding saved-metadata batch.
            while(job.ready.size()<32 && System.nanoTime()<until) {
                var candidate=job.incoming.poll();if(candidate==null)break;job.ready.addLast(candidate);
            }
            if(job.boundsReady && job.ready.size()<=32 && !job.savedDone && !job.savedBusy && state.metadataPending==0
                    && System.nanoTime()<until)
                saved(state, job);
            while (state.nativePending < Math.min(job.limits.concurrentChunks(), state.policy.current().concurrentChunks()) && !job.ready.isEmpty() && System.nanoTime() < until)
                request(state, job, job.ready.removeFirst());
        } catch (RuntimeException failure) { failed(job, failure); return; }
        if(job.enumerationDone && job.incoming.isEmpty() && job.loaded.isEmpty() && job.savedDone && job.ready.isEmpty() && job.pending==0) {
            stop(job, job.limited ? "partial" : "complete", true); return;
        }
        if (System.nanoTime() >= job.nextProgress) {
            boolean player = job.owner.player();
            job.nextProgress = System.nanoTime() + TimeUnit.SECONDS.toNanos(player ? 2 : 10);
            var progress=progress(job);
            var recipient=job.owner.current(job.level.getServer(),job.level.dimension());
            if(recipient!=null && recipient.getEntity() instanceof ServerPlayer current)current.displayClientMessage(progress,true);
            else reply(job,() -> progress);
        }
    }
    private void saved(State state, Job job) {
        var scanner = job.level.getChunkSource().chunkScanner();
        if (!(scanner instanceof StructureSearchStorage storage))
            throw new IllegalStateException("Chunk storage does not expose the native search inventory");
        job.savedBusy = true; state.metadataPending++;
        long began = System.nanoTime();
        try {
            CompletableFuture<?> task;
            if (job.regions == null) {
                long reach = job.radius + locateOffset(job) + 16;
                int minX = Math.toIntExact(Math.floorDiv(Math.max(-30000000L, job.x - reach), 16));
                int minZ = Math.toIntExact(Math.floorDiv(Math.max(-30000000L, job.z - reach), 16));
                int maxX = Math.toIntExact(Math.floorDiv(Math.min(29999999L, job.x + reach), 16));
                int maxZ = Math.toIntExact(Math.floorDiv(Math.min(29999999L, job.z + reach), 16));
                task = storage.skyweave$regions(minX, minZ, maxX, maxZ, job.limits.candidateLimit()).thenAccept(regions ->
                        job.level.getServer().execute(() -> {
                            if (!job.running) return;
                            job.regions=regions.positions();if(regions.limited())job.limited=true;
                        }));
            } else if (!job.stored.isEmpty()) {
                var batch = new ArrayList<ChunkPos>();
                while (!job.stored.isEmpty() && batch.size() < 32) batch.add(job.stored.removeFirst());
                var scans = batch.stream().map(pos -> {
                    var fields = new CollectFields(new FieldSelector(IntTag.TYPE, "DataVersion"),
                            new FieldSelector(CompoundTag.TYPE, "structures"));
                    return scanner.scanChunk(pos, fields).thenApply(unused ->
                            SavedStructureStarts.possible((CompoundTag)fields.getResult(), job.targetIds) ? pos : null);
                }).toList();
                task = CompletableFuture.allOf(scans.toArray(CompletableFuture[]::new)).thenRun(() ->
                        job.level.getServer().execute(() -> {
                            if (!job.running) return;
                            job.savedChunks += batch.size();
                            for (var scan : scans) {
                                var pos = scan.getNow(null);
                                if(pos!=null && reserve(job,pos)==Reservation.NEW)
                                    job.ready.addLast(candidate(job,pos,List.of()));
                            }
                        }));
            } else if (job.nextRegion < job.regions.size()) {
                var region = job.regions.get(job.nextRegion++);
                task = storage.skyweave$storedChunks(region).thenAccept(positions -> job.level.getServer().execute(() -> {
                    if (!job.running) return;
                    job.savedRegions++;
                    for (var pos : positions) if (StructureSearchBounds.withinWorld(pos.x, pos.z)
                            && inRange(job, pos) && !job.scheduled.contains(pos)) job.stored.addLast(pos);
                }));
            } else {
                job.savedDone = true; job.savedBusy = false; state.metadataPending--; return;
            }
            task.whenComplete((unused, failure) -> job.level.getServer().execute(() -> {
                state.metadataPending--;
                if (!job.running || state.job != job) return;
                job.metadataNanos += System.nanoTime() - began;
                job.savedBusy = false;
                if (failure != null) failed(job, failure);
            }));
        } catch (RuntimeException failure) {
            state.metadataPending--; job.savedBusy = false; throw failure;
        }
    }
    private void request(State state, Job job, Candidate candidate) {
        job.pending++; state.nativePending++;
        long began = System.nanoTime();
        var cache = job.level.getChunkSource(); var pos = candidate.position();
        try {
            tickets(job).addTicket(TICKET, pos, TICKET_LEVEL, job.id); job.leases.add(pos);
            // This exact API's server-thread branch managedBlocks. The worker branch marshals registration safely.
            CompletableFuture.supplyAsync(() -> job.running
                    ? cache.getChunkFuture(pos.x, pos.z, ChunkStatus.STRUCTURE_STARTS, true)
                    : CompletableFuture.<ChunkResult<ChunkAccess>>completedFuture(null), state.requests)
                    .thenCompose(future -> future).whenComplete((chunk, failure) -> job.level.getServer().execute(() -> {
                release(job, pos); state.nativePending--;
                if (!job.running || state.job != job) return;
                job.pending--;
                job.nativeNanos += System.nanoTime() - began;
                if (failure != null) { failed(job, failure); return; }
                if (chunk == null) { job.rejected++; return; }
                if (!chunk.isSuccess()) { failed(job, new IllegalStateException(chunk.getError())); return; }
                try { inspect(job, candidate, chunk.orElse(null), true); job.verified++; }
                catch (RuntimeException error) { failed(job, error); }
            }));
        } catch (RuntimeException failure) {
            release(job, pos); state.nativePending--; job.pending--; throw failure;
        }
    }
    private static void inspect(Job job, Candidate candidate, ChunkAccess chunk, boolean publishFirst) {
        for (var target : job.targets) {
            var start = chunk.getStartForStructure(target.structure().value());
            if (start == null || !start.isValid()) continue;
            var serialization = net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext.fromLevel(job.level);
            var physicalChunk = PhysicalStructureIdentity.referenceChunk(start, serialization);
            var position = target.placements().stream().map(placement -> placement.getLocatePos(physicalChunk))
                    .min(Comparator.comparingInt((net.minecraft.core.BlockPos p) -> p.getX())
                            .thenComparingInt(p -> p.getZ()).thenComparingInt(p -> p.getY()))
                    .orElseGet(() -> new net.minecraft.core.BlockPos(physicalChunk.getMiddleBlockX(), 0, physicalChunk.getMiddleBlockZ()));
            if (position.getY() == 0) position = position.atY(PhysicalStructureIdentity.referenceBounds(start, serialization).minY() + 1);
            long distance = distance(job, position);
            if (distance > (long)job.radius * job.radius) continue;
            String key = job.identities.computeIfAbsent(start, value -> PhysicalStructureIdentity.of(
                    target.structure().unwrapKey().orElseThrow().location(), value,
                    serialization));
            if (job.seen.contains(key)) continue;
            if (job.seen.size() == job.limits.candidateLimit()) { job.limited = true; return; }
            boolean firstResult = job.seen.isEmpty();
            job.seen.add(key);
            var match = new Match(target.structure().getRegisteredName(), position, distance);
            job.matches.add(match);
            if (firstResult && publishFirst) reply(job,() -> result(match));
            job.matches.sort(ORDER); if (job.matches.size() > 64) job.matches.removeLast();
        }
    }
    private static long distance(Job job, net.minecraft.core.BlockPos position) {
        long dx = position.getX() - (long)job.x, dz = position.getZ() - (long)job.z; return dx * dx + dz * dz;
    }
    private static long minimumDistance(Job job, net.minecraft.core.BlockPos position) {
        return StructureSearchBounds.minimumDistance(position.getX(), position.getZ(), job.x, job.z, job.sourceShift);
    }
    private static DistanceManager tickets(Job job) { return ((StructureSearchCacheAccess)job.level.getChunkSource()).skyweave$searchTickets(); }
    private static void release(Job job, ChunkPos pos) {
        if (job.leases.remove(pos)) tickets(job).removeTicket(TICKET, pos, TICKET_LEVEL, job.id);
    }
    private void stop(Job job, String outcome, boolean notify) {
        job.running=false;job.outcome=outcome;job.ready.clear();job.incoming.clear();job.loaded.clear();job.stored.clear();
        if(job.enumerationTask!=null)job.enumerationTask.cancel(false);
        var state=servers.get(job.level.getServer());if(state!=null)state.worker.purge();
        for (var pos : List.copyOf(job.leases)) release(job, pos);
        if (notify) {
            reply(job,() -> text(outcome,job.verified+job.rejected,job.seen.size()));
            if(outcome.equals("partial"))reply(job,() -> text(System.nanoTime()>=job.deadline?"limit_time":"limit_coverage"));
            var recipient=job.owner.current(job.level.getServer(),job.level.dimension());if(recipient!=null)page(recipient,1);
        }
    }
    private void failed(Job job, Throwable failure) {
        if (!job.running) return;
        job.failureReason = failure.toString();
        stop(job,"failed",false);job.owner.failure(job.level.getServer(),job.level.dimension(),text("failed",job.failureReason));
    }
    private static boolean validOwner(Job job) {
        return job.level.getServer().getLevel(job.level.dimension())==job.level
                && job.owner.current(job.level.getServer(),job.level.dimension())!=null;
    }
    private static void reply(Job job,java.util.function.Supplier<Component> message) {
        job.owner.success(job.level.getServer(),job.level.dimension(),message);
    }
    private Job owned(CommandSourceStack source) {
        var state = servers.get(source.getServer()); var job = state == null ? null : state.job;
        return job!=null && source.getLevel()==job.level && job.owner.owns(source)?job:null;
    }
    private int status(CommandSourceStack source) {
        var job = owned(source); if (job == null) return fail(source, "missing");
        if (job.outcome.equals("failed")) { source.sendFailure(text("failed", job.failureReason)); return 0; }
        source.sendSuccess(() -> job.running?progress(job):text(job.outcome,job.verified+job.rejected,job.seen.size()),false);
        source.sendSuccess(() -> text("work", job.clusters, job.savedRegions, job.savedChunks, job.pending), false);
        source.sendSuccess(() -> text("timing", Math.round(job.enumerationNanos / 1e6), Math.round(job.metadataNanos / 1e6),
                Math.round(job.nativeNanos / 1e6)), false);
        var state=servers.get(source.getServer());
        source.sendSuccess(() -> text("pipeline",job.loaded.size(),job.incoming.size()+job.ready.size(),job.pending,
                state==null?0:state.nativePending),false);
        return 1;
    }
    private static Component progress(Job job) {
        return text("progress_stream",text(!job.boundsReady?"phase_planning":!job.enumerationDone?"phase_searching":"phase_verifying"),
                job.verified+job.rejected,job.scheduled.size(),job.seen.size());
    }
    private int page(CommandSourceStack source, int page) {
        var job = owned(source); if (job == null) return fail(source, "missing");
        int first = (page - 1) * 10, pages = Math.max(1, Math.ceilDiv(job.matches.size(), 10));
        if (page > pages) return fail(source, "page_missing");
        if (job.matches.isEmpty()) {
            source.sendSuccess(() -> text(job.running ? "empty_running"
                    : job.outcome.equals("complete") ? "empty_complete" : "empty_partial"), false);
            if (job.running) source.sendSuccess(() -> action("cancel", "cancel"), false);
            return 0;
        }
        source.sendSuccess(() -> text("page", page, pages, job.running || !job.outcome.equals("complete")
                ? text("provisional") : text("verified")), false);
        for (var match : job.matches.subList(Math.min(first, job.matches.size()), Math.min(first + 10, job.matches.size()))) {
            source.sendSuccess(() -> result(match), false);
        }
        var navigation = Component.empty();
        if (page > 1) navigation.append(action("previous", "page " + (page - 1))).append(" ");
        if (page < pages) navigation.append(action("next", "page " + (page + 1))).append(" ");
        if (job.running) navigation.append(action("cancel", "cancel"));
        if (!navigation.getSiblings().isEmpty()) source.sendSuccess(() -> navigation, false);
        return job.matches.size();
    }
    private static Component result(Match match) {
        var p = match.position();
        var coordinates = Component.literal("[" + p.getX() + ", " + p.getY() + ", " + p.getZ() + "]").withStyle(style ->
                style.withColor(ChatFormatting.AQUA).withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                        "/tp @s " + p.getX() + " " + p.getY() + " " + p.getZ())));
        return text("result", match.id(), coordinates, Math.round(Math.sqrt(match.distance())))
                .copy().withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, text("result_help"))));
    }
    private static Component action(String label, String suffix) {
        return text(label).copy().withStyle(style -> style.withColor(ChatFormatting.YELLOW)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/skyweavecompat structures " + suffix)));
    }
    private static Component text(String key, Object... args) { return LocatorText.text("structures", key, args); }
    private static int fail(CommandSourceStack source, String key) { source.sendFailure(text(key)); return 0; }
}
