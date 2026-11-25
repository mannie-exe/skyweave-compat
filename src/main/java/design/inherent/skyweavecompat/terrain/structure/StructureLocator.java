package design.inherent.skyweavecompat.terrain.structure;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import design.inherent.reform.minecraft.command.SearchCommands;
import design.inherent.reform.minecraft.command.SearchRecipient;
import design.inherent.reform.operation.SearchReport;
import design.inherent.reform.operation.WorkBudget;
import design.inherent.reform.owner.PolicyRefusal;
import design.inherent.skyweavecompat.mixin.StructureSearchCacheAccess;
import design.inherent.skyweavecompat.mixin.StructureSearchChunksAccess;
import design.inherent.skyweavecompat.terrain.LocatorText;
import design.inherent.skyweavecompat.configuration.SettingsPolicy;
import design.inherent.skyweavecompat.configuration.SettingsReload;
import design.inherent.skyweavecompat.configuration.ConfigLocations;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
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
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.io.IOException;
import java.io.UncheckedIOException;

/** Resumable discovery of actual native starts; never calls synchronous locate or waits on a server tick. */
public final class StructureLocator {
    private static final TicketType<UUID> TICKET = TicketType.create("skyweave_structure_search", UUID::compareTo);
    private static final int TICKET_LEVEL = ChunkLevel.byStatus(ChunkStatus.STRUCTURE_STARTS);
    private static final Map<MinecraftServer, State> servers = new IdentityHashMap<>();
    private final SearchCommands<Match> reporting = new SearchCommands<>("skyweavecompat structures", source -> {
        var state = servers.get(source.getServer());
        return Optional.ofNullable(state == null ? null : state.job).map(job -> job.publication);
    });
    private static final class State {
        final SettingsPolicy<SearchSettings> policy;
        final WorkBudget work;
        State(MinecraftServer server, SearchSettings settings) {
            Runnable thread = () -> SettingsReload.requireThread(server);
            policy = new SettingsPolicy<>(settings, thread);
            work = new WorkBudget(SearchSettings.MAX_CONCURRENT_CHUNKS, thread);
        }
        final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(4), task -> {
                    var thread = new Thread(task, "Skyweave structure requests"); thread.setDaemon(true); return thread;
                });
        // Enumeration can wait on bounded delivery; native admission must not queue behind its producer.
        final ThreadPoolExecutor requests = new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(2),task -> {
                    var thread=new Thread(task,"Skyweave structure admission");thread.setDaemon(true);return thread;
                });
        // Disk membership must not run on a tick, nor queue behind a producer awaiting delivery.
        final ThreadPoolExecutor membership = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(4), task -> {
                    var thread = new Thread(task, "Skyweave structure membership"); thread.setDaemon(true); return thread;
                });
        Job job;
        int metadataPending;
        final java.util.concurrent.atomic.AtomicInteger retiring = new java.util.concurrent.atomic.AtomicInteger();
        final java.util.concurrent.atomic.AtomicInteger nativePending = new java.util.concurrent.atomic.AtomicInteger();
    }
    private record Target(Holder<Structure> structure, List<RandomSpreadStructurePlacement> placements) {}
    private record Selection(List<Target> targets, int unplaced, int unsupported) {}
    private record Grid(int spacing, int separation, RandomSpreadType spread, int salt, int customIdentity) {}
    private record Candidate(ChunkPos position, long distance) {}
    private record Enumeration(boolean limited) {}
    private enum Reservation { NEW, DUPLICATE }
    private record Ownership(StructureDistribution distribution, StructureFamily family) {}
    private record Match(String id, net.minecraft.core.BlockPos position, long distance) {}
    private record Found(byte[] identity, Match match) {}
    private static final Comparator<Match> ORDER = Comparator.comparingLong(Match::distance).thenComparing(Match::id)
            .thenComparingLong(match -> match.position().asLong());
    private static final class Job {
        final UUID id;
        final SearchRecipient recipient;
        final SearchReport<Match> report;
        final WorkBudget.Group work;
        final SearchCommands.Job<Match> publication;
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
        long matched;
        final Map<String, Integer> targetOrdinals = new HashMap<>();
        final Map<net.minecraft.world.level.levelgen.structure.StructureStart, String> identities = new LinkedHashMap<>(128, .75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<net.minecraft.world.level.levelgen.structure.StructureStart, String> entry) {
                return size() > 256;
            }
        };
        final ArrayList<Match> matches = new ArrayList<>();
        final long began = System.nanoTime();
        volatile boolean running = true;
        final ArrayBlockingQueue<Candidate> incoming = new ArrayBlockingQueue<>(64);
        final ArrayDeque<ChunkHolder> loaded = new ArrayDeque<>();
        volatile boolean boundsReady, enumerationDone;
        Future<?> enumerationTask;
        boolean boundsReported;
        long verified, rejected, savedChunks, savedRegions;
        volatile long clusters;
        volatile long enumerationNanos;
        long metadataNanos, nativeNanos;
        long ended;
        boolean savedDone, savedBusy;
        volatile StructureSearchStorage.RegionScan regionScan;
        final ArrayDeque<ChunkPos> regions = new ArrayDeque<>();
        boolean regionsComplete;
        final ArrayDeque<ChunkPos> stored = new ArrayDeque<>();
        final SearchLedger scheduled;
        final SearchLedger seen;
        final ArrayDeque<Candidate> ready = new ArrayDeque<>();
        volatile boolean limited;
        Job(CommandSourceStack source, int radius, List<Target> targets, SearchSettings limits,
                WorkBudget.Group work, java.util.function.Consumer<Job> cancel) {
            recipient = SearchRecipient.capture(source, 2);
            level = recipient.level(); this.radius = radius; this.targets = targets;
            report = new SearchReport<>(SearchReport.Limits.STANDARD, () -> SettingsReload.requireThread(level.getServer()));
            id = report.id();
            this.work = work;
            publication = new SearchCommands.Job<>(recipient, report, work, StructureLocator::result, () -> cancel.accept(this));
            targetIds = targets.stream().map(t -> t.structure().getRegisteredName()).collect(java.util.stream.Collectors.toUnmodifiableSet());
            for (var name : targetIds.stream().sorted().toList()) targetOrdinals.put(name, targetOrdinals.size());
            placements = targets.stream().flatMap(t -> t.placements().stream()).distinct().toList();
            locateOffset = Math.max(8, placements.stream().map(p -> p.getLocatePos(new ChunkPos(0, 0)))
                    .mapToLong(p -> Math.max(Math.abs((long)p.getX()), Math.abs((long)p.getZ()))).max().orElse(8));
            x = net.minecraft.util.Mth.floor(recipient.origin().x); z = net.minecraft.util.Mth.floor(recipient.origin().z);
            this.limits = limits;
            var temporary = Path.of(System.getProperty("java.io.tmpdir"));
            scheduled = new SearchLedger(8, limits.membershipBatchSize(), temporary);
            seen = new SearchLedger(36, limits.membershipBatchSize(), temporary);
        }
    }
    public static void register() {
        var locator = new StructureLocator();
        NeoForge.EVENT_BUS.addListener((ServerAboutToStartEvent event) -> servers.put(event.getServer(),
                new State(event.getServer(), SearchSettings.load(ConfigLocations.ownedDirectory(FMLPaths.CONFIGDIR.get())))));
        SettingsReload.registerPolicy("search", SearchSettings.FILE, design.inherent.skyweavecompat.configuration.PolicyDefinitions.SEARCH,
                server -> servers.get(server).policy);
        NeoForge.EVENT_BUS.addListener(locator::commands);
        NeoForge.EVENT_BUS.addListener(locator::tick);
        NeoForge.EVENT_BUS.addListener((LevelEvent.Unload event) -> {
            if (!(event.getLevel() instanceof ServerLevel level)) return;
            var state = servers.get(level.getServer());
            if (state != null && state.job != null && state.job.level == level) {
                locator.stop(state.job, "cancelled", false);
                state.job = null;
            }
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> {
            var state = servers.get(event.getServer());
            if (state != null) {
                if (state.job != null) locator.stop(state.job,"cancelled",false);
                state.policy.close();
                state.worker.shutdownNow();
                for (var queued : state.requests.shutdownNow()) ((ChunkAdmission<?>)queued).discard();
                state.membership.shutdown();
                servers.remove(event.getServer());
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
    private void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("skyweavecompat").requires(source -> source.hasPermission(2))
                .then(reporting.branch().requires(source -> source.hasPermission(2))
                    .then(Commands.literal("search").then(Commands.argument("radius", IntegerArgumentType.integer(1, 131072))
                        .then(Commands.argument("target", StringArgumentType.greedyString()).suggests((ctx, builder) -> {
                            for (var value : suggestions(ctx.getSource())) if (value.startsWith(builder.getRemaining())) builder.suggest(value);
                            return builder.buildFuture();
                        }).executes(ctx -> start(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius"), StringArgumentType.getString(ctx, "target"))))))
                    .then(Commands.literal("details").executes(ctx -> details(ctx.getSource())))));
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
            if (state.retiring.get() != 0 || state.work.inUse() != 0 || state.metadataPending != 0
                    || state.worker.getActiveCount() != 0) return fail(source, "settling");
            var selection = targets(source, selector);
            var limits = state.policy.current();
            var job = new Job(source, radius, selection.targets(), limits, state.work.group(limits.concurrentChunks()),
                    selectionJob -> stop(selectionJob, "cancelled", true));
            job.limited = selection.unsupported() > 0;
            if (Math.abs((long)job.x) > 29999983 || Math.abs((long)job.z) > 29999983)
                throw new IllegalArgumentException("Search origin is outside Minecraft's coordinate bounds");
            state.job = job;
            source.sendSuccess(() -> text("started", radius).copy().append(" ").append(action("cancel", "cancel " + job.id)), false);
            if (selection.unplaced() > 0) source.sendSuccess(() -> text("unplaced", selection.unplaced())
                    .copy().withStyle(ChatFormatting.YELLOW), false);
            if (selection.unsupported() > 0) source.sendSuccess(() -> text("unsupported_entries", selection.unsupported())
                    .copy().withStyle(ChatFormatting.YELLOW), false);
            // Loaded proto/full chunks may contain unsaved starts anywhere, including old placements.
            var chunks = (StructureSearchChunksAccess)source.getLevel().getChunkSource().chunkMap;
            var holders = new HashSet<ChunkHolder>();
            for (var sourceHolders : List.of(chunks.skyweave$searchChunks(),
                    chunks.skyweave$searchUpdatingChunks().values(), chunks.skyweave$searchUnloadingChunks().values())) {
                for (var holder : sourceHolders) {
                    if (holders.contains(holder)) continue;
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
        } catch (PolicyRefusal refused) { return fail(source, "missing"); }
        catch (IllegalArgumentException failure) { source.sendFailure(text("invalid", failure.getMessage())); return 0; }
    }
    private static Enumeration enumerate(Job job) {
        var bounds = new StructureSearchBounds(job.level, () -> job.running);
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
            var cells = distribution.searchCursor(job.x, job.z, job.radius, locateOffset(job) + 16);
            while (!cells.complete()) {
                if (!job.running) return new Enumeration(true);
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
            var cells = placement.searchCursor(job.x, job.z, job.radius, locateOffset(job) + 16);
            while (!cells.complete()) {
                if (!job.running) return new Enumeration(true);
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
                // Preserve the original perimeter order without scanning each ring's interior again.
                for (int dx = -ring; dx <= ring; dx++) for (int dz = -ring; dz <= ring;
                        dz += Math.abs(dx) == ring ? 1 : 2 * ring) {
                    if (!job.running) return new Enumeration(true);
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
        if (!job.running) return Reservation.DUPLICATE;
        try { return job.scheduled.add(ByteBuffer.allocate(8).putLong(position.toLong()).array()) ? Reservation.NEW : Reservation.DUPLICATE; }
        catch (IOException error) { throw new UncheckedIOException(error); }
    }
    private static boolean publish(Job job,Candidate candidate) {
        var reservation=reserve(job,candidate.position());
        if(reservation!=Reservation.NEW)return reservation==Reservation.DUPLICATE;
        while(job.running)try {
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
        if (job.recipient.current().isEmpty()) { stop(job, "cancelled", false); return; }
        long until = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(job.limits.tickBudgetMillis());
        try {
            while(!job.loaded.isEmpty() && System.nanoTime()<until
                    && state.work.inUse() < Math.min(job.limits.concurrentChunks(), state.policy.current().concurrentChunks())) {
                var chunk=job.loaded.removeFirst().getLatestChunk();
                if(chunk!=null) {
                    var reservation = job.work.tryReserve(state.policy.current().concurrentChunks()).orElseThrow();
                    try { inspect(state, job, chunk, reservation::close); }
                    catch (RuntimeException error) { reservation.close(); throw error; }
                }
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
            while (state.work.inUse() < Math.min(job.limits.concurrentChunks(), state.policy.current().concurrentChunks()) && !job.ready.isEmpty() && System.nanoTime() < until)
                request(state, job, job.ready.removeFirst());
        } catch (RuntimeException failure) { failed(job, failure); return; }
        if(job.enumerationDone && job.incoming.isEmpty() && job.loaded.isEmpty() && job.savedDone && job.ready.isEmpty() && job.work.pending()==0) {
            boolean complete = !job.limited && job.verified + job.rejected == job.scheduled.size();
            stop(job, complete ? "complete" : "partial", true); return;
        }
        observe(job, false);
        reporting.notifyMatches(job.publication, event.getServer().getTickCount());
    }
    private void saved(State state, Job job) {
        var scanner = job.level.getChunkSource().chunkScanner();
        if (!(scanner instanceof StructureSearchStorage storage))
            throw new IllegalStateException("Chunk storage does not expose the native search inventory");
        job.savedBusy = true; state.metadataPending++;
        long began = System.nanoTime();
        try {
            CompletableFuture<?> task;
            if (job.regionScan == null) {
                long reach = job.radius + locateOffset(job) + 16;
                int minX = Math.toIntExact(Math.floorDiv(Math.max(-30000000L, job.x - reach), 16));
                int minZ = Math.toIntExact(Math.floorDiv(Math.max(-30000000L, job.z - reach), 16));
                int maxX = Math.toIntExact(Math.floorDiv(Math.min(29999999L, job.x + reach), 16));
                int maxZ = Math.toIntExact(Math.floorDiv(Math.min(29999999L, job.z + reach), 16));
                task = storage.skyweave$scanRegions(minX, minZ, maxX, maxZ).thenAccept(scan -> {
                    // Transfer cursor ownership before queuing a callback that a stopping server may never run.
                    boolean keep;
                    synchronized (job) { keep = job.running; if (keep) job.regionScan = scan; }
                    if (!keep) try { scan.close(); } catch (IOException error) { throw new UncheckedIOException(error); }
                });
            } else if (!job.stored.isEmpty()) {
                var batch = new ArrayList<ChunkPos>();
                while (!job.stored.isEmpty() && batch.size() < 32) batch.add(job.stored.removeFirst());
                var scans = batch.stream().map(pos -> {
                    var fields = new CollectFields(new FieldSelector(IntTag.TYPE, "DataVersion"),
                            new FieldSelector(CompoundTag.TYPE, "structures"));
                    return scanner.scanChunk(pos, fields).thenApply(unused ->
                            SavedStructureStarts.possible((CompoundTag)fields.getResult(), job.targetIds) ? pos : null);
                }).toList();
                task = CompletableFuture.allOf(scans.toArray(CompletableFuture[]::new))
                        .thenApplyAsync(unused -> {
                            var admitted = new ArrayList<ChunkPos>();
                            for (var scan : scans) {
                                var pos = scan.getNow(null);
                                if (pos != null && reserve(job, pos) == Reservation.NEW) admitted.add(pos);
                            }
                            return admitted;
                        }, state.membership).thenAccept(admitted -> job.level.getServer().execute(() -> {
                            if (!job.running) return;
                            job.savedChunks += batch.size();
                            for (var pos : admitted) job.ready.addLast(candidate(job, pos, List.of()));
                        }));
            } else if (!job.regions.isEmpty()) {
                var region = job.regions.removeFirst();
                task = storage.skyweave$storedChunks(region).thenAccept(positions -> job.level.getServer().execute(() -> {
                    if (!job.running) return;
                    job.savedRegions++;
                    for (var pos : positions) if (StructureSearchBounds.withinWorld(pos.x, pos.z) && inRange(job, pos)) job.stored.addLast(pos);
                }));
            } else if (!job.regionsComplete) {
                task = job.regionScan.next().thenAccept(page -> job.level.getServer().execute(() -> {
                    if (!job.running) return;
                    job.regions.addAll(page.positions()); job.regionsComplete = page.complete();
                    if (page.limited()) job.limited = true;
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
        var reservation = job.work.tryReserve(state.policy.current().concurrentChunks()).orElseThrow();
        long began = System.nanoTime();
        var cache = job.level.getChunkSource(); var pos = candidate.position();
        state.nativePending.incrementAndGet();
        try {
            job.leases.add(pos);
            reservation.attachTicket(() -> release(job, pos));
            tickets(job).addTicket(TICKET, pos, TICKET_LEVEL, job.id);
            // This exact API's server-thread branch managedBlocks. The worker branch marshals registration safely.
            var admission = new ChunkAdmission<>(() -> job.running
                    ? cache.getChunkFuture(pos.x, pos.z, ChunkStatus.STRUCTURE_STARTS, true)
                    : CompletableFuture.<ChunkResult<ChunkAccess>>completedFuture(null));
            admission.result.whenComplete((chunk, failure) -> {
                state.nativePending.decrementAndGet();
                job.level.getServer().execute(() -> {
                if (!job.running || state.job != job) { reservation.close(); return; }
                job.nativeNanos += System.nanoTime() - began;
                if (failure != null) { reservation.close(); failed(job, failure); return; }
                if (chunk == null) { reservation.close(); job.rejected++; return; }
                if (!chunk.isSuccess()) { reservation.close(); failed(job, new IllegalStateException(chunk.getError())); return; }
                try { inspect(state, job, chunk.orElse(null), () -> { job.verified++; reservation.close(); }); }
                catch (RuntimeException error) { reservation.close(); failed(job, error); }
                });
            });
            state.requests.execute(admission);
        } catch (RuntimeException | Error failure) {
            state.nativePending.decrementAndGet();
            reservation.close(); throw failure;
        }
    }
    private void inspect(State state, Job job, ChunkAccess chunk, Runnable finished) {
        var found = new ArrayList<Found>();
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
            var match = new Match(target.structure().getRegisteredName(), position, distance);
            var digest = HexFormat.of().parseHex(key.substring(key.lastIndexOf('/') + 1));
            var identity = ByteBuffer.allocate(36).putInt(job.targetOrdinals.get(match.id())).put(digest).array();
            found.add(new Found(identity, match));
        }
        if (found.isEmpty()) { finished.run(); return; }
        CompletableFuture.supplyAsync(() -> {
            var admitted = new ArrayList<Match>();
            if (!job.running) return admitted;
            try {
                for (var value : found) if (job.running && job.seen.add(value.identity())) admitted.add(value.match());
                return admitted;
            } catch (IOException error) { throw new UncheckedIOException(error); }
        }, state.membership).whenComplete((admitted, failure) -> job.level.getServer().execute(() -> {
            try {
                if (!job.running || state.job != job) return;
                if (failure != null) { failed(job, failure); return; }
                job.matched += admitted.size();
                for (var match : admitted) {
                    job.matches.add(match); job.matches.sort(ORDER);
                    if (job.matches.size() > 64) job.matches.removeLast();
                }
            } finally { finished.run(); }
        }));
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
        stop(job, outcome, notify, "");
    }
    private void stop(Job job, String outcome, boolean notify, String failure) {
        if (!job.running) return;
        observe(job, outcome.equals("complete"));
        job.ended = System.nanoTime();
        StructureSearchStorage.RegionScan scan;
        synchronized (job) { job.running = false; scan = job.regionScan; job.regionScan = null; }
        job.ready.clear();job.incoming.clear();job.loaded.clear();job.stored.clear();job.regions.clear();job.identities.clear();
        if(job.enumerationTask!=null)job.enumerationTask.cancel(false);
        var state=servers.get(job.level.getServer());if(state!=null)state.worker.purge();
        if (state != null) {
            state.retiring.incrementAndGet();
            state.membership.execute(() -> {
                try (var chunks = job.scheduled; var identities = job.seen; var inventory = scan) {
                    // All retirement IO stays off the server thread, including a producer merge in flight.
                } catch (IOException error) { com.mojang.logging.LogUtils.getLogger().error("Cannot retire structure search {}", job.id, error); }
                finally { state.retiring.decrementAndGet(); }
            });
        }
        job.work.stop();
        job.work.releaseTickets();
        var terminal = switch (outcome) {
            case "complete" -> SearchReport.Outcome.COMPLETE;
            case "partial" -> SearchReport.Outcome.PARTIAL;
            case "failed" -> SearchReport.Outcome.FAILED;
            default -> SearchReport.Outcome.CANCELLED;
        };
        if (terminal == SearchReport.Outcome.CANCELLED) job.report.requestCancellation(job.id);
        String reason = switch (terminal) {
            case COMPLETE -> "";
            case PARTIAL -> text("limit_coverage").getString();
            case FAILED -> failure.substring(0, Math.min(512, failure.length()));
            case CANCELLED -> text("cancelled", job.verified + job.rejected, job.matched).getString();
            default -> throw new IllegalStateException("Structure search must finish with a terminal outcome");
        };
        job.report.finish(terminal, reason);
        if (notify) {
            var page = job.report.capture(1).orElseThrow().cursor();
            reply(job, () -> text(outcome, job.verified + job.rejected, job.matched).copy().append(" ")
                    .append(action("results_action", "page at " + page.job() + " " + page.revision() + " 1")));
            if(outcome.equals("partial"))reply(job,() -> text("limit_coverage"));
            reporting.notifyMatches(job.publication, job.level.getServer().getTickCount());
        }
    }
    private void failed(Job job, Throwable failure) {
        if (!job.running) return;
        stop(job, "failed", false, failure.toString());
        job.recipient.current().ifPresent(source -> source.sendFailure(text("failed", failure.toString())));
    }
    private static void observe(Job job, boolean finished) {
        long enumerated = job.scheduled.size();
        job.report.observe(new SearchReport.Progress(enumerated, job.verified, job.rejected,
                        finished ? OptionalLong.of(enumerated) : OptionalLong.empty()),
                job.matched, job.matches);
    }
    private static void reply(Job job,java.util.function.Supplier<Component> message) {
        job.recipient.current().ifPresent(source -> source.sendSuccess(message, false));
    }
    private Job owned(CommandSourceStack source) {
        var state = servers.get(source.getServer()); var job = state == null ? null : state.job;
        return job != null && job.recipient.owns(source) ? job : null;
    }
    private int details(CommandSourceStack source) {
        var job = owned(source); if (job == null) return fail(source, "missing");
        source.sendSuccess(() -> progress(job), false);
        source.sendSuccess(() -> text("work", job.clusters, job.savedRegions, job.savedChunks, job.work.pending()), false);
        source.sendSuccess(() -> text("timing", Math.round(job.enumerationNanos / 1e6), Math.round(job.metadataNanos / 1e6),
                Math.round(job.nativeNanos / 1e6)), false);
        source.sendSuccess(() -> text("elapsed", TimeUnit.NANOSECONDS.toMillis(
                (job.running ? System.nanoTime() : job.ended) - job.began)), false);
        var state=servers.get(source.getServer());
        source.sendSuccess(() -> text("pipeline",job.loaded.size(),job.incoming.size()+job.ready.size(),job.work.pending(),
                state.nativePending.get()),false);
        source.sendSuccess(() -> text("metadata_pipeline",state.metadataPending,job.regions.size(),job.stored.size()),false);
        return 1;
    }
    private static Component progress(Job job) {
        return text("progress_stream",text(!job.boundsReady?"phase_planning":!job.enumerationDone?"phase_searching"
                        : !job.savedDone && job.work.pending()==0 ? "phase_saved" : "phase_verifying"),
                job.verified+job.rejected,job.scheduled.size(),job.matched);
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
