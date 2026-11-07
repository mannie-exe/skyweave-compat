package design.inherent.skyweavecompat.terrain;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.sathrek.sky_archipelago.config.SkyIslandSettings;
import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandShapeArchetype;
import org.sathrek.sky_archipelago.worldgen.generator.field.model.IslandDescriptor;
import org.sathrek.sky_archipelago.worldgen.generator.field.model.IslandFamily;
import java.util.*;
import java.util.concurrent.*;

/** Operator-only planned-center discovery. Native field caches support concurrent worldgen reads. */
public final class IslandLocator {
    private static final int PAGE_SIZE = 10, BATCH_CELLS = 256;
    private final Map<MinecraftServer, State> servers = new IdentityHashMap<>();
    private static final class State {
        final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1), task -> {
                    var thread = new Thread(task, "Skyweave island metadata"); thread.setDaemon(true); return thread;
                });
        Job job;
    }
    private record Match(String id, int x, int y, int z, long distanceSquared, String archetype,
            String exterior, String interior, String biome, String edge, String family) {}
    private static final class Job {
        final CommandSourceStack source;
        final ResourceKey<Level> dimension;
        final ClusterField field;
        final IslandStyles styles;
        final SkyIslandSettings settings;
        final int x, z, radius;
        final Map<String, String> filters;
        final IslandSearch<Match> search;
        volatile boolean cancelled;
        Future<?> task;
        boolean running;
        Job(CommandSourceStack source, ClusterField field, IslandStyles styles, SkyIslandSettings settings,
                int radius, Map<String, String> filters, GenerationProfile profile) {
            this.source = source; dimension = source.getLevel().dimension(); this.field = field;
            this.styles = styles; this.settings = settings; this.radius = radius; this.filters = filters;
            x = net.minecraft.util.Mth.floor(source.getPosition().x); z = net.minecraft.util.Mth.floor(source.getPosition().z);
            search = new IslandSearch<>(x, z, radius, profile.spacing(), profile.lookupCells(),
                    Comparator.comparingLong(Match::distanceSquared).thenComparing(Match::id));
        }
    }

    public static void register() {
        var locator = new IslandLocator();
        NeoForge.EVENT_BUS.addListener(locator::commands);
        NeoForge.EVENT_BUS.addListener((LevelEvent.Unload event) -> {
            if (!(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level)) return;
            var state = locator.servers.get(level.getServer());
            if (state != null && state.job != null && state.job.dimension.equals(level.dimension())) {
                cancel(state);
            }
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> {
            var state = locator.servers.remove(event.getServer());
            if (state != null) { cancel(state); state.worker.shutdownNow(); }
        });
    }
    private void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("skyweavecompat").requires(source -> source.hasPermission(2))
                .then(Commands.literal("islands")
                    .executes(ctx -> page(ctx.getSource(), 1))
                    .then(Commands.literal("search")
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 131072))
                            .executes(ctx -> start(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius"), ""))
                            .then(Commands.argument("filters", StringArgumentType.greedyString())
                                .suggests((ctx, builder) -> {
                                    String remaining = builder.getRemaining();
                                    int offset = remaining.lastIndexOf(' ') + 1;
                                    var tail = builder.createOffset(builder.getStart() + offset);
                                    for (String suggestion : suggestions()) if (suggestion.startsWith(tail.getRemaining())) tail.suggest(suggestion);
                                    return tail.buildFuture();
                                })
                                .executes(ctx -> start(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius"),
                                        StringArgumentType.getString(ctx, "filters"))))))
                    .then(Commands.literal("more").executes(ctx -> more(ctx.getSource())))
                    .then(Commands.literal("page").then(Commands.argument("number", IntegerArgumentType.integer(1, 7))
                            .executes(ctx -> page(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "number")))))
                    .then(Commands.literal("cancel").executes(ctx -> cancel(ctx.getSource())))));
    }
    private int start(CommandSourceStack source, int radius, String raw) {
        try {
            if (!(source.getLevel().getChunkSource().getGenerator() instanceof SkyIslandChunkGenerator generator)
                    || !((GeologicalGenerator)(Object)generator).skyweave$isEnabled())
                return fail(source, "unsupported");
            var field = ((GeologicalGenerator)(Object)generator).skyweave$field(source.getLevel().getChunkSource().randomState());
            var profile = ((ProfiledTerrain)(Object)field).skyweave$profile();
            var filters = filters(raw);
            var state = servers.computeIfAbsent(source.getServer(), ignored -> new State());
            if (state.job != null && state.job.running) return fail(source, "busy");
            var job = new Job(source, (ClusterField)(Object)field, ((StyledTerrain)(Object)field).skyweave$styles(),
                    generator.resolvedSkyIslandSettings(), radius, filters, profile);
            if (state.job != null) state.job.cancelled = true;
            state.job = job; launch(source.getServer(), state, job);
            source.sendSuccess(() -> text("started", radius), false); return 1;
        } catch (IllegalArgumentException error) {
            source.sendFailure(text("invalid", error.getMessage())); return 0;
        }
    }
    private Job owned(CommandSourceStack source) {
        var state = servers.get(source.getServer()); var job = state == null ? null : state.job;
        return job != null && job.source.getTextName().equals(source.getTextName())
                && job.dimension.equals(source.getLevel().dimension()) ? job : null;
    }
    private int more(CommandSourceStack source) {
        var job = owned(source);
        if (job == null) return fail(source, "missing");
        if (job.running) return fail(source, "busy");
        if (job.search.complete()) return page(source, 1);
        launch(source.getServer(), servers.get(source.getServer()), job); return 1;
    }
    private int cancel(CommandSourceStack source) {
        var job = owned(source);
        if (job == null) return fail(source, "missing");
        cancel(servers.get(source.getServer()));
        source.sendSuccess(() -> text("cancelled"), false); return 1;
    }
    private static void cancel(State state) {
        var job = state.job;
        if (job == null) return;
        job.cancelled = true;
        // Remove cancelled queued batches without interrupting a shared native cache computation.
        if (job.task != null) job.task.cancel(false);
        state.worker.purge(); state.job = null;
    }
    private void launch(MinecraftServer server, State state, Job job) {
        job.running = true;
        try { job.task = state.worker.submit(() -> {
            Throwable failure = null;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            int end = Math.min(job.search.total(), job.search.visited() + BATCH_CELLS);
            try {
                while (!job.cancelled && !Thread.currentThread().isInterrupted() && !job.search.complete()
                        && job.search.visited() < end && System.nanoTime() < deadline) {
                    var cell = job.search.next();
                    var matches = job.field.skyweave$members(cell.x(), cell.z(), job.settings).stream()
                            .map(island -> match(job, cell, island)).toList();
                    job.search.accept(matches, match -> match.distanceSquared() <= (long)job.radius * job.radius
                            && job.filters.entrySet().stream().allMatch(filter -> value(match, filter.getKey()).equals(filter.getValue())));
                }
            } catch (RuntimeException error) { failure = error; }
            Throwable result = failure;
            server.execute(() -> {
                job.running = false;
                if (job.cancelled || state.job != job || servers.get(server) != state) return;
                if (result != null) {
                    job.source.sendFailure(text("failed", result.toString())); state.job = null;
                } else page(job.source, 1);
            });
        }); } catch (RejectedExecutionException error) {
            job.running = false; state.job = null; job.source.sendFailure(text("busy"));
        }
    }
    private Match match(Job job, ClusterLayout.Cell cell, IslandDescriptor island) {
        var style = job.styles.style(island);
        String edge = OceanIslands.wetArchetype(island.archetype())
                ? name(OceanConfiguration.profile().mode(island.seed())) : "none";
        long dx = (long)island.centerX() - job.x, dz = (long)island.centerZ() - job.z;
        return new Match(cell.x() + "," + cell.z() + "/" + Long.toUnsignedString(island.seed(), 16),
                island.centerX(), island.centerY(), island.centerZ(), dx * dx + dz * dz, name(island.archetype()),
                name(style.kind()), name(IslandInteriors.kind(island, InteriorConfiguration.profile())),
                style.kind() == IslandStyles.Kind.DEEP_DARK ? "minecraft:deep_dark" : style.hostBiome(), edge, name(island.family()));
    }
    private int page(CommandSourceStack source, int page) {
        var job = owned(source);
        if (job == null) return fail(source, "missing");
        if (job.running) return fail(source, "busy");
        var results = job.search.results(); int first = (page - 1) * PAGE_SIZE;
        if (first >= results.size() && page != 1) return fail(source, "page_missing");
        source.sendSuccess(() -> text(job.search.complete() ? "complete" : "partial",
                job.search.visited(), job.search.total(), job.search.matches(), results.size()), false);
        for (var match : results.subList(Math.min(first, results.size()), Math.min(first + PAGE_SIZE, results.size()))) {
            double dx = match.x() - (double)job.x, dz = match.z() - (double)job.z;
            String direction = (dz < 0 ? "N" : dz > 0 ? "S" : "") + (dx < 0 ? "W" : dx > 0 ? "E" : "");
            source.sendSuccess(() -> text("result", match.x(), match.y(), match.z(),
                    Math.round(Math.sqrt(match.distanceSquared())), direction, match.archetype(), match.exterior(),
                    match.interior(), match.biome(), match.edge(), match.id()), false);
        }
        if (results.isEmpty()) source.sendSuccess(() -> text(job.search.complete() ? "no_match" : "no_match_yet"), false);
        return results.size();
    }
    static Map<String, String> filters(String raw) {
        var filters = new LinkedHashMap<String, String>(); filters.put("family", "anchor_plateau");
        var seen = new HashSet<String>();
        for (String token : raw.strip().isEmpty() ? new String[0] : raw.strip().split("\\s+")) {
            String[] pair = token.split("=", -1);
            if (pair.length != 2 || pair[1].isEmpty() || !seen.add(pair[0])) throw new IllegalArgumentException("Expected unique key=value filters");
            String key = pair[0], value = pair[1].toLowerCase(Locale.ROOT);
            boolean valid = switch (key) {
                case "archetype" -> Arrays.stream(IslandShapeArchetype.values()).anyMatch(v -> name(v).equals(value));
                case "exterior" -> Arrays.stream(IslandStyles.Kind.values()).anyMatch(v -> name(v).equals(value));
                case "interior" -> Arrays.stream(IslandInteriors.Kind.values()).anyMatch(v -> name(v).equals(value));
                case "family" -> value.equals("any") || Arrays.stream(IslandFamily.values()).anyMatch(v -> name(v).equals(value));
                case "edge" -> Set.of("none", "native", "coastal", "perimeter").contains(value);
                case "biome" -> net.minecraft.resources.ResourceLocation.tryParse(value) != null && value.contains(":");
                default -> false;
            };
            if (!valid) throw new IllegalArgumentException("Unsupported filter: " + token);
            if (key.equals("family") && value.equals("any")) filters.remove(key); else filters.put(key, value);
        }
        if (filters.containsKey("edge") && !filters.get("edge").equals("none") && filters.containsKey("archetype")
                && !filters.get("archetype").equals("lagoon")) throw new IllegalArgumentException("Ocean edge filters require lagoon");
        return Map.copyOf(filters);
    }
    private static String value(Match match, String key) {
        return switch (key) {
            case "archetype" -> match.archetype(); case "exterior" -> match.exterior();
            case "interior" -> match.interior(); case "biome" -> match.biome();
            case "edge" -> match.edge(); case "family" -> match.family(); default -> throw new AssertionError(key);
        };
    }
    private static List<String> suggestions() {
        var values = new ArrayList<String>();
        for (var value : IslandShapeArchetype.values()) values.add("archetype=" + name(value));
        for (var value : IslandStyles.Kind.values()) values.add("exterior=" + name(value));
        for (var value : IslandInteriors.Kind.values()) values.add("interior=" + name(value));
        for (var value : IslandFamily.values()) values.add("family=" + name(value));
        values.addAll(List.of("edge=native", "edge=coastal", "edge=perimeter", "edge=none", "family=any", "biome=minecraft:"));
        return values;
    }
    private static String name(Enum<?> value) { return value.name().toLowerCase(Locale.ROOT); }
    private static Component text(String key, Object... args) { return Component.translatable("skyweave_compat.islands." + key, args); }
    private static int fail(CommandSourceStack source, String key) { source.sendFailure(text(key)); return 0; }
    private IslandLocator() {}
}
