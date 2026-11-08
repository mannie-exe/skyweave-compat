package design.inherent.skyweavecompat.terrain;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
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
            String exterior, String interior, String biome, String edge, String apron, String family) {}
    private record Snapshot(int visited, int total, long matches, List<Match> results) {
        boolean complete() { return visited == total; }
    }
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
        boolean running = true, inFlight;
        long nextProgress;
        Snapshot snapshot;
        Job(CommandSourceStack source, ClusterField field, IslandStyles styles, SkyIslandSettings settings,
                int radius, Map<String, String> filters, GenerationProfile profile) {
            this.source = source; dimension = source.getLevel().dimension(); this.field = field;
            this.styles = styles; this.settings = settings; this.radius = radius; this.filters = filters;
            x = net.minecraft.util.Mth.floor(source.getPosition().x); z = net.minecraft.util.Mth.floor(source.getPosition().z);
            search = new IslandSearch<>(x, z, radius, profile.spacing(), profile.lookupCells(),
                    Comparator.comparingLong(Match::distanceSquared).thenComparing(Match::id));
            snapshot = new Snapshot(0, search.total(), 0, List.of());
            nextProgress = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        }
    }

    public static void register() {
        var locator = new IslandLocator();
        NeoForge.EVENT_BUS.addListener(locator::commands);
        NeoForge.EVENT_BUS.addListener(locator::tick);
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
                    .then(Commands.literal("status").executes(ctx -> status(ctx.getSource())))
                    // Older command history remains usable, without submitting duplicate slices.
                    .then(Commands.literal("more").executes(ctx -> status(ctx.getSource())))
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
            state.job = job;
            source.sendSuccess(() -> text("started", radius).copy().append(" ")
                    .append(action("cancel_action", "cancel")), false);
            launch(source.getServer(), state, job); return 1;
        } catch (IllegalArgumentException error) {
            source.sendFailure(text("invalid", error.getMessage())); return 0;
        }
    }
    private Job owned(CommandSourceStack source) {
        var state = servers.get(source.getServer()); var job = state == null ? null : state.job;
        return job != null && (job.source.getEntity() instanceof net.minecraft.server.level.ServerPlayer
                ? job.source.getEntity() == source.getEntity() : job.source.getTextName().equals(source.getTextName()))
                && job.dimension.equals(source.getLevel().dimension()) ? job : null;
    }
    private int status(CommandSourceStack source) {
        var job = owned(source);
        if (job == null) return fail(source, "missing");
        var snapshot = job.snapshot;
        source.sendSuccess(() -> text(job.running ? "progress" : "status_complete",
                snapshot.visited(), snapshot.total(), snapshot.matches()).copy()
                .append(job.running ? Component.literal(" ").append(action("cancel_action", "cancel")) : Component.empty()), false);
        return 1;
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
    private void tick(ServerTickEvent.Post event) {
        var server = event.getServer();
        var state = servers.get(server);
        var job = state == null ? null : state.job;
        if (job == null) return;
        if (!validOwner(server, job)) {
            cancel(state); return;
        }
        if (!job.running) return;
        if (!job.inFlight) launch(server, state, job);
        if (System.nanoTime() >= job.nextProgress) {
            boolean player = job.source.getEntity() instanceof net.minecraft.server.level.ServerPlayer;
            job.nextProgress = System.nanoTime() + TimeUnit.SECONDS.toNanos(player ? 2 : 10);
            var snapshot = job.snapshot;
            var message = text("progress", snapshot.visited(), snapshot.total(), snapshot.matches());
            if (player) ((net.minecraft.server.level.ServerPlayer)job.source.getEntity()).displayClientMessage(message, true);
            else job.source.sendSuccess(() -> message, false);
        }
    }
    private static boolean validOwner(MinecraftServer server, Job job) {
        return !(job.source.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)
                || server.getPlayerList().getPlayer(player.getUUID()) == player
                    && player.serverLevel().dimension().equals(job.dimension) && player.hasPermissions(2);
    }
    private void launch(MinecraftServer server, State state, Job job) {
        job.inFlight = true;
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
            var snapshot = new Snapshot(job.search.visited(), job.search.total(), job.search.matches(), job.search.results());
            server.execute(() -> {
                if (job.cancelled || state.job != job || servers.get(server) != state) return;
                if (!validOwner(server, job)) { cancel(state); return; }
                job.inFlight = false;
                job.snapshot = snapshot;
                if (result != null) {
                    job.source.sendFailure(text("failed", result.toString())); state.job = null;
                } else if (snapshot.complete()) {
                    job.running = false;
                    job.source.sendSuccess(() -> text("complete", snapshot.visited(), snapshot.total(), snapshot.matches()), false);
                    page(job.source, 1);
                }
                // The next server tick schedules the next bounded worker slice.
            });
        }); } catch (RejectedExecutionException error) {
            job.running = false; state.job = null; job.source.sendFailure(text("busy"));
        }
    }
    private Match match(Job job, ClusterLayout.Cell cell, IslandDescriptor island) {
        var style = job.styles.style(island);
        String apron = style.disturbed()
                ? GranularEdges.disintegrating(island.seed(), job.styles.profile().edges()) ? "disintegrating" : "mixed" : "none";
        String edge = OceanIslands.wetArchetype(island.archetype())
                ? name(OceanConfiguration.profile().mode(island.seed())) : apron;
        long dx = (long)island.centerX() - job.x, dz = (long)island.centerZ() - job.z;
        return new Match(cell.x() + "," + cell.z() + "/" + Long.toUnsignedString(island.seed(), 16),
                island.centerX(), island.centerY(), island.centerZ(), dx * dx + dz * dz, name(island.archetype()),
                name(style.kind()), name(IslandInteriors.kind(island, InteriorConfiguration.profile())),
                style.kind() == IslandStyles.Kind.DEEP_DARK ? "minecraft:deep_dark" : style.hostBiome(), edge, apron, name(island.family()));
    }
    private int page(CommandSourceStack source, int page) {
        var job = owned(source);
        if (job == null) return fail(source, "missing");
        var snapshot = job.snapshot;
        var results = snapshot.results(); int first = (page - 1) * PAGE_SIZE;
        if (first >= results.size() && page != 1) return fail(source, "page_missing");
        int pages = Math.max(1, (results.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        source.sendSuccess(() -> text(job.running ? "page_provisional" : "page", page, pages, results.size()), false);
        for (var match : results.subList(Math.min(first, results.size()), Math.min(first + PAGE_SIZE, results.size()))) {
            double dx = match.x() - (double)job.x, dz = match.z() - (double)job.z;
            String direction = (dz < 0 ? "N" : dz > 0 ? "S" : "") + (dx < 0 ? "W" : dx > 0 ? "E" : "");
            var coordinates = Component.literal("[" + match.x() + ", " + match.y() + ", " + match.z() + "]")
                    .withStyle(style -> style.withColor(ChatFormatting.AQUA)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                            "/tp @s " + match.x() + " " + match.y() + " " + match.z())));
            var details = text("result_details", match.archetype(), match.exterior(), match.interior(),
                    match.biome(), match.edge(), match.apron(), match.family(), match.id());
            source.sendSuccess(() -> text("result", coordinates, Math.round(Math.sqrt(match.distanceSquared())),
                    direction, match.exterior().equals("native") ? match.archetype() : match.archetype() + " / " + match.exterior())
                    .copy().withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, details))), false);
        }
        if (results.isEmpty()) source.sendSuccess(() -> text(job.running ? "no_match_yet" : "no_match"), false);
        var navigation = Component.empty();
        if (page > 1) navigation.append(action("previous_action", "page " + (page - 1))).append(" ");
        if (page < pages) navigation.append(action("next_action", "page " + (page + 1))).append(" ");
        if (job.running) navigation.append(action("cancel_action", "cancel"));
        if (!navigation.getSiblings().isEmpty()) source.sendSuccess(() -> navigation, false);
        return results.size();
    }
    private static Component action(String label, String suffix) {
        return text(label).copy().withStyle(style -> style.withColor(ChatFormatting.YELLOW)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/skyweavecompat islands " + suffix)));
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
                case "edge" -> Set.of("none", "native", "coastal", "perimeter", "mixed", "disintegrating").contains(value);
                case "apron" -> Set.of("none", "mixed", "disintegrating").contains(value);
                case "biome" -> net.minecraft.resources.ResourceLocation.tryParse(value) != null && value.contains(":");
                default -> false;
            };
            if (!valid) throw new IllegalArgumentException("Unsupported filter: " + token);
            if (key.equals("family") && value.equals("any")) filters.remove(key); else filters.put(key, value);
        }
        if (Set.of("native", "coastal", "perimeter").contains(filters.getOrDefault("edge", "none")) && filters.containsKey("archetype")
                && !filters.get("archetype").equals("lagoon")) throw new IllegalArgumentException("Ocean edge filters require lagoon");
        if (Set.of("mixed", "disintegrating").contains(filters.getOrDefault("edge", "none")) && filters.containsKey("archetype")
                && IslandShapeArchetype.valueOf(filters.get("archetype").toUpperCase(Locale.ROOT)).waterBased())
            throw new IllegalArgumentException("Granular edges require a land archetype");
        return Map.copyOf(filters);
    }
    private static String value(Match match, String key) {
        return switch (key) {
            case "archetype" -> match.archetype(); case "exterior" -> match.exterior();
            case "interior" -> match.interior(); case "biome" -> match.biome();
            case "edge" -> match.edge(); case "apron" -> match.apron(); case "family" -> match.family(); default -> throw new AssertionError(key);
        };
    }
    private static List<String> suggestions() {
        var values = new ArrayList<String>();
        for (var value : IslandShapeArchetype.values()) values.add("archetype=" + name(value));
        for (var value : IslandStyles.Kind.values()) values.add("exterior=" + name(value));
        for (var value : IslandInteriors.Kind.values()) values.add("interior=" + name(value));
        for (var value : IslandFamily.values()) values.add("family=" + name(value));
        values.addAll(List.of("edge=native", "edge=coastal", "edge=perimeter", "edge=mixed", "edge=disintegrating", "edge=none", "family=any", "biome=minecraft:"));
        values.addAll(List.of("apron=none", "apron=mixed", "apron=disintegrating"));
        return values;
    }
    private static String name(Enum<?> value) { return value.name().toLowerCase(Locale.ROOT); }
    private static Component text(String key, Object... args) {
        return LocatorText.text("islands", key, args);
    }
    private static int fail(CommandSourceStack source, String key) { source.sendFailure(text(key)); return 0; }
    private IslandLocator() {}
}
