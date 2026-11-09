package design.inherent.skyweavecompat.terrain.structure;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import design.inherent.skyweavecompat.mixin.StructureSearchCacheAccess;
import design.inherent.skyweavecompat.mixin.StructureSearchChunksAccess;
import design.inherent.skyweavecompat.terrain.LocatorText;
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
    private final Map<MinecraftServer, State> servers = new IdentityHashMap<>();
    private static final class State {
        final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(4), task -> {
                    var thread = new Thread(task, "Skyweave structure requests"); thread.setDaemon(true); return thread;
                });
        Job job;
        int nativePending, metadataPending;
    }
    private record Target(Holder<Structure> structure, List<RandomSpreadStructurePlacement> placements) {}
    private record Selection(List<Target> targets, int unplaced, int unsupported) {}
    private record Grid(int spacing, int separation, RandomSpreadType spread, int salt, int customIdentity) {}
    private record Candidate(ChunkPos position, List<RandomSpreadStructurePlacement> placements, long distance) {}
    private record Enumeration(List<Candidate> candidates, boolean limited) {}
    private record Ownership(StructureDistribution distribution, StructureFamily family) {}
    private record Match(String id, net.minecraft.core.BlockPos position, long distance) {}
    private static final Comparator<Match> ORDER = Comparator.comparingLong(Match::distance).thenComparing(Match::id)
            .thenComparingLong(match -> match.position().asLong());
    private static final class Job {
        final UUID id = UUID.randomUUID();
        final CommandSourceStack source;
        final ServerLevel level;
        final int x, z, radius;
        final SearchSettings limits;
        final List<Target> targets;
        final Set<String> targetIds;
        final List<RandomSpreadStructurePlacement> placements;
        final long locateOffset;
        final Set<ChunkPos> leases = new HashSet<>();
        final Set<String> seen = new HashSet<>();
        final ArrayList<Match> matches = new ArrayList<>();
        final long deadline;
        volatile boolean running = true;
        List<Candidate> candidates;
        int next, verified, rejected, pending, savedChunks, savedRegions;
        volatile int clusters;
        volatile long enumerationNanos;
        long metadataNanos, nativeNanos;
        boolean savedDone, savedBusy;
        List<ChunkPos> regions;
        int nextRegion;
        final ArrayDeque<ChunkPos> stored = new ArrayDeque<>();
        final Set<ChunkPos> scheduled = new HashSet<>();
        final ArrayDeque<Candidate> ready = new ArrayDeque<>();
        boolean limited;
        String outcome = "progress", failureReason = "";
        long nextProgress;
        Job(CommandSourceStack source, int radius, List<Target> targets) {
            this.source = source; level = source.getLevel(); this.radius = radius; this.targets = targets;
            targetIds = targets.stream().map(t -> t.structure().getRegisteredName()).collect(java.util.stream.Collectors.toUnmodifiableSet());
            placements = targets.stream().flatMap(t -> t.placements().stream()).distinct().toList();
            locateOffset = Math.max(8, placements.stream().map(p -> p.getLocatePos(new ChunkPos(0, 0)))
                    .mapToLong(p -> Math.max(Math.abs((long)p.getX()), Math.abs((long)p.getZ()))).max().orElse(8));
            x = net.minecraft.util.Mth.floor(source.getPosition().x); z = net.minecraft.util.Mth.floor(source.getPosition().z);
            limits = SearchSettings.active();
            long now = System.nanoTime(); deadline = now + TimeUnit.SECONDS.toNanos(limits.timeLimitSeconds());
            nextProgress = now + TimeUnit.SECONDS.toNanos(2);
        }
    }
    public static void register() {
        var locator = new StructureLocator();
        NeoForge.EVENT_BUS.addListener(locator::commands);
        NeoForge.EVENT_BUS.addListener(locator::tick);
        NeoForge.EVENT_BUS.addListener((LevelEvent.Unload event) -> {
            if (!(event.getLevel() instanceof ServerLevel level)) return;
            var state = locator.servers.get(level.getServer());
            if (state != null && state.job != null && state.job.level == level) locator.stop(state.job, "cancelled", false);
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> {
            var state = locator.servers.remove(event.getServer());
            if (state != null) { if (state.job != null) locator.stop(state.job, "cancelled", false); state.worker.shutdownNow(); }
        });
    }
    private void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("skyweavecompat").requires(source -> source.hasPermission(2))
                .then(Commands.literal("structures").executes(ctx -> page(ctx.getSource(), 1))
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
            var state = servers.computeIfAbsent(source.getServer(), ignored -> new State());
            if (state.job != null && state.job.running) return fail(source, "busy");
            var selection = targets(source, selector);
            var job = new Job(source, radius, selection.targets());
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
            var holders = new HashSet<ChunkHolder>(); chunks.skyweave$searchChunks().forEach(holders::add);
            holders.addAll(chunks.skyweave$searchUpdatingChunks().values());
            holders.addAll(chunks.skyweave$searchUnloadingChunks().values());
            for (var holder : holders) {
                var chunk = holder.getLatestChunk();
                if (chunk != null && inRange(job, chunk.getPos())) inspect(job, candidate(job, chunk.getPos(), List.of()), chunk, false);
            }
            if (!job.matches.isEmpty()) {
                var nearestLoaded = job.matches.getFirst(); source.sendSuccess(() -> result(nearestLoaded), false);
            }
            state.worker.execute(() -> {
                try {
                    long began = System.nanoTime();
                    var result = enumerate(job);
                    job.enumerationNanos = System.nanoTime() - began;
                    source.getServer().execute(() -> {
                        if (!job.running || state.job != job) return;
                        job.candidates = new ArrayList<>(result.candidates()); job.limited |= result.limited();
                        for (var candidate : job.candidates) job.scheduled.add(candidate.position());
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
        var candidates = new HashMap<ChunkPos, Candidate>();
        long seed = job.level.getSeed(); boolean limited = false;
        var distributions = owned.keySet().stream().map(Ownership::distribution).distinct().toList();
        for (var distribution : distributions) {
            var cells = distribution.searchOwners(job.x, job.z, job.radius, locateOffset(job) + 16);
            while (!cells.complete()) {
                if (!job.running || System.nanoTime() >= job.deadline) return new Enumeration(sorted(candidates), true);
                var cell = cells.next(); job.clusters++;
                var owner = new StructureSelection.Owner(cell.x(), cell.z());
                for (var entry : owned.entrySet()) {
                    if (entry.getKey().distribution() != distribution) continue;
                    for (var site : distribution.searchSites(owner, entry.getKey().family())) {
                        var pos = new ChunkPos(site.chunkX(), site.chunkZ());
                        if (Math.abs((long)pos.getMinBlockX()) >= 30000000 || Math.abs((long)pos.getMinBlockZ()) >= 30000000) continue;
                        if (!distribution.possibleNewStart(entry.getKey().family(), pos.x, pos.z)) continue;
                        if (!distribution.possibleSearchVariant(owner, entry.getKey().family(), site, ownedTargets.get(entry.getKey()))) continue;
                        var candidate = candidate(job, pos, entry.getValue());
                        if (candidate.distance() > (long)job.radius * job.radius) continue;
                        if (!candidates.containsKey(pos) && candidates.size() == job.limits.candidateLimit())
                            return new Enumeration(sorted(candidates), true);
                        candidates.merge(pos, candidate, StructureLocator::merge);
                    }
                }
            }
        }
        for (var placement : villages) {
            var cells = placement.searchOwners(job.x, job.z, job.radius, locateOffset(job) + 16);
            while (!cells.complete()) {
                if (!job.running || System.nanoTime() >= job.deadline) return new Enumeration(sorted(candidates), true);
                var cell = cells.next(); job.clusters++;
                for (var site : placement.searchSites(cell.x(), cell.z(), job.x, job.z, job.radius, locateOffset(job) + 16)) {
                    var pos = new ChunkPos(site.chunkX(), site.chunkZ());
                    if (Math.abs((long)pos.getMinBlockX()) >= 30000000 || Math.abs((long)pos.getMinBlockZ()) >= 30000000) continue;
                    var candidate = candidate(job, pos, List.of(placement));
                    if (candidate.distance() > (long)job.radius * job.radius) continue;
                    if (!candidates.containsKey(pos) && candidates.size() == job.limits.candidateLimit())
                        return new Enumeration(sorted(candidates), true);
                    candidates.merge(pos, candidate, StructureLocator::merge);
                }
            }
        }
        // Rings visit nearby source regions first; only a fully exhausted domain claims completeness.
        for (int ring = 0; ; ring++) {
            boolean any = false;
            for (var grid : grids.entrySet()) {
                int spacing = grid.getKey().spacing();
                long offset = grid.getValue().stream().map(p -> p.getLocatePos(new ChunkPos(0, 0)))
                        .mapToLong(p -> Math.max(Math.abs((long)p.getX()), Math.abs((long)p.getZ()))).max().orElse(0);
                long reach = Math.ceilDiv(job.radius + offset + 16, spacing * 16L) + 1;
                if (ring > reach) continue;
                any = true;
                int cx = Math.floorDiv(Math.floorDiv(job.x, 16), spacing), cz = Math.floorDiv(Math.floorDiv(job.z, 16), spacing);
                for (int dx = -ring; dx <= ring; dx++) for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                    if (!job.running || System.nanoTime() >= job.deadline) return new Enumeration(sorted(candidates), true);
                    var placement = grid.getValue().getFirst();
                    var pos = placement.getPotentialStructureChunk(seed, (cx + dx) * spacing, (cz + dz) * spacing);
                    if (Math.abs((long)pos.getMinBlockX()) >= 30000000 || Math.abs((long)pos.getMinBlockZ()) >= 30000000) continue;
                    long nearest = Long.MAX_VALUE;
                    for (var p : grid.getValue()) nearest = Math.min(nearest, distance(job, p.getLocatePos(pos)));
                    if (nearest > (long)job.radius * job.radius) continue;
                    var old = candidates.get(pos);
                    List<RandomSpreadStructurePlacement> placements = grid.getValue();
                    if (old != null) {
                        var combined = new ArrayList<>(old.placements()); combined.addAll(placements); placements = List.copyOf(combined);
                    }
                    if (old == null && candidates.size() == job.limits.candidateLimit()) { limited = true; continue; }
                    candidates.put(pos, new Candidate(pos, placements, Math.min(nearest, old == null ? Long.MAX_VALUE : old.distance())));
                }
            }
            if (!any || limited) break;
        }
        return new Enumeration(sorted(candidates), limited);
    }
    private static Candidate merge(Candidate first, Candidate second) {
        var placements = new LinkedHashSet<>(first.placements()); placements.addAll(second.placements());
        return new Candidate(first.position(), List.copyOf(placements), Math.min(first.distance(), second.distance()));
    }
    private static long locateOffset(Job job) {
        return job.locateOffset;
    }
    private static Candidate candidate(Job job, ChunkPos pos, List<RandomSpreadStructurePlacement> placements) {
        var effective = placements.isEmpty() ? job.placements : placements;
        long distance = effective.stream().mapToLong(p -> distance(job, p.getLocatePos(pos)))
                .min().orElseGet(() -> distance(job, new net.minecraft.core.BlockPos(pos.getMiddleBlockX(), 0, pos.getMiddleBlockZ())));
        if (placements.isEmpty() && job.targets.stream().anyMatch(t -> t.placements().isEmpty()))
            distance = Math.min(distance, distance(job, new net.minecraft.core.BlockPos(pos.getMiddleBlockX(), 0, pos.getMiddleBlockZ())));
        return new Candidate(pos, List.copyOf(placements), distance);
    }
    private static boolean inRange(Job job, ChunkPos pos) {
        return candidate(job, pos, List.of()).distance() <= (long)job.radius * job.radius;
    }
    private static List<Candidate> sorted(Map<ChunkPos, Candidate> candidates) {
        return candidates.values().stream().sorted(Comparator.comparingLong(Candidate::distance)
                .thenComparingLong(candidate -> candidate.position().toLong())).toList();
    }
    private void tick(ServerTickEvent.Post event) {
        var state = servers.get(event.getServer()); var job = state == null ? null : state.job;
        if (job == null || !job.running) return;
        if (!validOwner(job)) { stop(job, "cancelled", false); return; }
        if (System.nanoTime() >= job.deadline) { stop(job, "partial", true); return; }
        long until = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(job.limits.tickBudgetMillis());
        try {
            if (job.candidates != null && job.ready.size() < 64)
                while (job.next < job.candidates.size() && job.ready.size() < 64) job.ready.addLast(job.candidates.get(job.next++));
            if (job.candidates != null && !job.savedDone && !job.savedBusy && state.metadataPending == 0)
                saved(state, job);
            while (state.nativePending < job.limits.concurrentChunks() && !job.ready.isEmpty() && System.nanoTime() < until)
                request(state, job, job.ready.removeFirst());
        } catch (RuntimeException failure) { failed(job, failure); return; }
        if (job.candidates != null && job.next == job.candidates.size() && job.savedDone && job.ready.isEmpty() && job.pending == 0) {
            stop(job, job.limited ? "partial" : "complete", true); return;
        }
        if (System.nanoTime() >= job.nextProgress) {
            boolean player = job.source.getEntity() instanceof ServerPlayer;
            job.nextProgress = System.nanoTime() + TimeUnit.SECONDS.toNanos(player ? 2 : 10);
            var progress = text("progress", job.verified + job.rejected, job.candidates == null ? 0 : job.candidates.size(), job.seen.size());
            if (player) ((ServerPlayer)job.source.getEntity()).displayClientMessage(progress, true);
            else job.source.sendSuccess(() -> progress, false);
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
                            job.regions = regions.positions(); job.limited |= regions.limited();
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
                                if (pos == null || !job.scheduled.add(pos)) continue;
                                if (job.candidates.size() == job.limits.candidateLimit()) { job.limited = true; continue; }
                                job.candidates.add(candidate(job, pos, List.of()));
                            }
                        }));
            } else if (job.nextRegion < job.regions.size()) {
                var region = job.regions.get(job.nextRegion++);
                task = storage.skyweave$storedChunks(region).thenAccept(positions -> job.level.getServer().execute(() -> {
                    if (!job.running) return;
                    job.savedRegions++;
                    for (var pos : positions) if (inRange(job, pos) && !job.scheduled.contains(pos)) job.stored.addLast(pos);
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
                    : CompletableFuture.<ChunkResult<ChunkAccess>>completedFuture(null), state.worker)
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
            var placement = target.placements().stream().filter(candidate.placements()::contains).findFirst().orElse(null);
            // A saved start remains valid even if its old placement no longer matches today's lattice.
            if (placement == null && !target.placements().isEmpty()) placement = target.placements().getFirst();
            var position = placement == null ? new net.minecraft.core.BlockPos(start.getChunkPos().getMiddleBlockX(),
                    0, start.getChunkPos().getMiddleBlockZ()) : placement.getLocatePos(start.getChunkPos());
            if (position.getY() == 0) position = position.atY(start.getBoundingBox().minY() + 1);
            long distance = distance(job, position);
            if (distance > (long)job.radius * job.radius) continue;
            String key = target.structure().getRegisteredName() + "/" + start.getChunkPos().toLong();
            if (job.seen.contains(key)) continue;
            if (job.seen.size() == job.limits.candidateLimit()) { job.limited = true; return; }
            boolean firstResult = job.seen.isEmpty();
            job.seen.add(key);
            var match = new Match(target.structure().getRegisteredName(), position, distance);
            job.matches.add(match);
            if (firstResult && publishFirst) job.source.sendSuccess(() -> result(match), false);
            job.matches.sort(ORDER); if (job.matches.size() > 64) job.matches.removeLast();
        }
    }
    private static long distance(Job job, net.minecraft.core.BlockPos position) {
        long dx = position.getX() - (long)job.x, dz = position.getZ() - (long)job.z; return dx * dx + dz * dz;
    }
    private static DistanceManager tickets(Job job) { return ((StructureSearchCacheAccess)job.level.getChunkSource()).skyweave$searchTickets(); }
    private static void release(Job job, ChunkPos pos) {
        if (job.leases.remove(pos)) tickets(job).removeTicket(TICKET, pos, TICKET_LEVEL, job.id);
    }
    private void stop(Job job, String outcome, boolean notify) {
        job.running = false; job.outcome = outcome; job.ready.clear();
        for (var pos : List.copyOf(job.leases)) release(job, pos);
        if (notify) {
            job.source.sendSuccess(() -> text(outcome, job.verified + job.rejected, job.seen.size()), false);
            if (outcome.equals("partial")) job.source.sendSuccess(() -> text(System.nanoTime() >= job.deadline
                    ? "limit_time" : "limit_coverage"), false);
            page(job.source, 1);
        }
    }
    private void failed(Job job, Throwable failure) {
        if (!job.running) return;
        job.failureReason = failure.toString();
        stop(job, "failed", false); job.source.sendFailure(text("failed", job.failureReason));
    }
    private static boolean validOwner(Job job) {
        return !(job.source.getEntity() instanceof ServerPlayer player)
                || job.level.getServer().getPlayerList().getPlayer(player.getUUID()) == player && player.serverLevel() == job.level && player.hasPermissions(2);
    }
    private Job owned(CommandSourceStack source) {
        var state = servers.get(source.getServer()); var job = state == null ? null : state.job;
        return job != null && source.getLevel() == job.level && (job.source.getEntity() instanceof ServerPlayer
                ? job.source.getEntity() == source.getEntity() : job.source.getTextName().equals(source.getTextName())) ? job : null;
    }
    private int status(CommandSourceStack source) {
        var job = owned(source); if (job == null) return fail(source, "missing");
        if (job.outcome.equals("failed")) { source.sendFailure(text("failed", job.failureReason)); return 0; }
        source.sendSuccess(() -> text(job.running ? "progress" : job.outcome, job.verified + job.rejected,
                job.running ? job.candidates == null ? 0 : job.candidates.size() : job.seen.size(), job.seen.size()), false);
        source.sendSuccess(() -> text("work", job.clusters, job.savedRegions, job.savedChunks, job.pending), false);
        source.sendSuccess(() -> text("timing", Math.round(job.enumerationNanos / 1e6), Math.round(job.metadataNanos / 1e6),
                Math.round(job.nativeNanos / 1e6)), false);
        return 1;
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
