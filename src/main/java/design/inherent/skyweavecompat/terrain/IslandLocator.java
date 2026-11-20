package design.inherent.skyweavecompat.terrain;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import design.inherent.reform.neoforge.command.SearchCommands;
import design.inherent.reform.neoforge.command.SearchRecipient;
import design.inherent.reform.operation.SearchReport;
import design.inherent.reform.operation.WorkBudget;
import design.inherent.reform.owner.PolicyRefusal;
import design.inherent.skyweavecompat.configuration.SettingsReload;
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
    private static final int BATCH_CELLS = 256;
    private final Map<MinecraftServer, State> servers = new IdentityHashMap<>();
    private final SearchCommands<Match> reporting = new SearchCommands<>("skyweavecompat islands", source -> {
        var state = servers.get(source.getServer());
        return Optional.ofNullable(state == null ? null : state.job).map(job -> job.publication);
    });
    private static final class State {
        final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1), task -> {
                    var thread = new Thread(task, "Skyweave island metadata"); thread.setDaemon(true); return thread;
                });
        final WorkBudget work;
        Job job;

        State(MinecraftServer server) { work = new WorkBudget(1, () -> SettingsReload.requireThread(server)); }
    }
    private record Match(String id, int x, int y, int z, long distanceSquared, String archetype,
            String exterior, String interior, String biome, String shoreline, String fringe, String family) {}
    private record Snapshot(int visited, int total, long matches, List<Match> results) {
        boolean complete() { return visited == total; }
    }
    private static final class Job {
        final SearchRecipient recipient;
        final SearchReport<Match> report;
        final SearchCommands.Job<Match> publication;
        final WorkBudget.Group work;
        final net.minecraft.server.level.ServerLevel level;
        final ResourceKey<Level> dimension;
        final ClusterField field;
        final IslandStyles styles;
        final SkyIslandSettings settings;
        final int x, z, radius;
        final Map<String, String> filters;
        final IslandSearch<Match> search;
        volatile boolean cancelled;
        Job(CommandSourceStack source, ClusterField field, IslandStyles styles, SkyIslandSettings settings,
                int radius, Map<String, String> filters, GenerationProfile profile, WorkBudget.Group work) {
            recipient = SearchRecipient.capture(source, 2);
            level = recipient.level(); dimension = level.dimension(); this.field = field;
            this.styles = styles; this.settings = settings; this.radius = radius; this.filters = filters;
            x = net.minecraft.util.Mth.floor(recipient.origin().x); z = net.minecraft.util.Mth.floor(recipient.origin().z);
            search = new IslandSearch<>(x, z, radius, profile.spacing(), profile.lookupCells(),
                    Comparator.comparingLong(Match::distanceSquared).thenComparing(Match::id));
            this.work = work;
            report = new SearchReport<>(SearchReport.Limits.STANDARD, () -> SettingsReload.requireThread(level.getServer()));
            observe(new Snapshot(0, search.total(), 0, List.of()));
            publication = new SearchCommands.Job<>(recipient, report, work, match -> result(this, match), () -> cancel(this));
        }

        void observe(Snapshot current) {
            report.observe(new SearchReport.Progress(current.visited(), current.visited(), 0, OptionalLong.of(current.total())),
                    current.matches(), current.results());
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
                cancel(state.job);
            }
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> {
            var state = locator.servers.remove(event.getServer());
            if (state != null) {
                if (state.job != null) cancel(state.job);
                state.worker.shutdownNow();
            }
        });
    }
    private void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("skyweavecompat").requires(source -> source.hasPermission(2))
                .then(reporting.branch().requires(source -> source.hasPermission(2))
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
                                        StringArgumentType.getString(ctx, "filters"))))))));
    }
    private int start(CommandSourceStack source, int radius, String raw) {
        try {
            if (!(source.getLevel().getChunkSource().getGenerator() instanceof SkyIslandChunkGenerator generator)
                    || !((GeologicalGenerator)(Object)generator).skyweave$isEnabled())
                return fail(source, "unsupported");
            var field = ((GeologicalGenerator)(Object)generator).skyweave$field(source.getLevel().getChunkSource().randomState());
            var profile = ((ProfiledTerrain)(Object)field).skyweave$profile();
            var filters = filters(raw);
            var state = servers.computeIfAbsent(source.getServer(), State::new);
            if (state.job != null && (!state.job.report.status().terminal() || state.job.work.pending() != 0))
                return fail(source, "busy");
            var job = new Job(source, (ClusterField)(Object)field, ((StyledTerrain)(Object)field).skyweave$styles(),
                    generator.resolvedSkyIslandSettings(), radius, filters, profile, state.work.group(1));
            state.job = job;
            source.sendSuccess(() -> text("started", radius).copy().append(" ")
                    .append(action("cancel_action", "cancel " + job.report.id())), false);
            launch(source.getServer(), state, job); return 1;
        } catch (PolicyRefusal refused) {
            return fail(source, "missing");
        } catch (IllegalArgumentException error) {
            source.sendFailure(text("invalid", error.getMessage())); return 0;
        }
    }
    private static void cancel(Job job) {
        if (job.report.status().terminal()) return;
        job.cancelled = true;
        job.report.requestCancellation(job.report.id());
        job.work.stop();
        if (job.work.pending() == 0) job.report.finish(SearchReport.Outcome.CANCELLED, "Island search cancelled");
    }
    private void tick(ServerTickEvent.Post event) {
        var server = event.getServer();
        var state = servers.get(server);
        var job = state == null ? null : state.job;
        if (job == null || job.report.status().terminal() || job.cancelled) return;
        if (job.recipient.current().isEmpty()) { cancel(job); return; }
        reporting.notifyMatches(job.publication, server.getTickCount());
        if (job.work.pending() == 0) launch(server, state, job);
    }
    private void launch(MinecraftServer server, State state, Job job) {
        var reservation = job.work.tryReserve().orElseThrow();
        try { state.worker.execute(() -> {
            Throwable failure = null;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            int end = Math.min(job.search.total(), job.search.visited() + BATCH_CELLS);
            try {
                while (!job.cancelled && !Thread.currentThread().isInterrupted() && !job.search.complete()
                        && job.search.visited() < end && System.nanoTime() < deadline) {
                    var cell = job.search.next();
                    var matches = job.field.skyweave$members(cell.x(), cell.z(), job.settings).stream()
                            .filter(island -> {
                                long dx = (long)island.centerX() - job.x, dz = (long)island.centerZ() - job.z;
                                return quickMatch(job.filters, dx * dx + dz * dz, (long)job.radius * job.radius,
                                        name(island.family()), name(island.archetype()));
                            })
                            .map(island -> match(job, cell, island)).toList();
                    job.search.accept(matches, match -> match.distanceSquared() <= (long)job.radius * job.radius
                            && job.filters.entrySet().stream().allMatch(filter -> value(match, filter.getKey()).equals(filter.getValue())));
                }
            } catch (RuntimeException error) { failure = error; }
            Throwable result = failure;
            var snapshot = new Snapshot(job.search.visited(), job.search.total(), job.search.matches(), job.search.results());
            server.execute(() -> {
                reservation.close();
                if (job.cancelled || state.job != job || servers.get(server) != state
                        || job.recipient.current().isEmpty()) { cancel(job); return; }
                job.observe(snapshot);
                if (result != null) {
                    job.work.stop();
                    var reason = result.toString();
                    job.report.finish(SearchReport.Outcome.FAILED, reason.substring(0, Math.min(512, reason.length())));
                    job.recipient.current().ifPresent(source -> source.sendFailure(text("failed", job.report.status().reason())));
                } else if (snapshot.complete()) {
                    job.work.stop();
                    job.report.finish(SearchReport.Outcome.COMPLETE, "");
                    reporting.notifyMatches(job.publication, server.getTickCount());
                    var page = job.report.capture(1).orElseThrow().cursor();
                    job.recipient.current().ifPresent(source -> source.sendSuccess(() ->
                            text("complete", snapshot.visited(), snapshot.total(), snapshot.matches()).copy().append(" ")
                                    .append(action("results_action", "page at " + page.job() + " " + page.revision() + " 1")), false));
                }
                // The next server tick schedules the next bounded worker slice.
            });
        }); } catch (RejectedExecutionException error) {
            reservation.close();
            job.work.stop();
            job.report.finish(SearchReport.Outcome.FAILED, "Island search worker unavailable");
            job.recipient.current().ifPresent(source -> source.sendFailure(text("busy")));
        }
    }
    /** Only descriptor-owned facts can reject before style/biome resolution. */
    static boolean quickMatch(Map<String, String> filters, long distance, long limit, String family, String archetype) {
        return distance <= limit && (!filters.containsKey("family") || filters.get("family").equals(family))
                && (!filters.containsKey("archetype") || filters.get("archetype").equals(archetype));
    }
    private Match match(Job job, ClusterLayout.Cell cell, IslandDescriptor island) {
        var style = job.styles.style(island);
        var components = IslandComponents.resolve(island, job.styles);
        long dx = (long)island.centerX() - job.x, dz = (long)island.centerZ() - job.z;
        return new Match(cell.x() + "," + cell.z() + "/" + Long.toUnsignedString(island.seed(), 16),
                island.centerX(), island.centerY(), island.centerZ(), dx * dx + dz * dz, name(island.archetype()),
                name(style.kind()), name(IslandInteriors.kind(island, InteriorConfiguration.profile(),style.kind())),
                style.kind() == IslandStyles.Kind.DEEP_DARK ? "minecraft:deep_dark" : style.hostBiome(), name(components.shoreline()), name(components.fringe()), name(island.family()));
    }
    private static Component result(Job job, Match match) {
        double dx = match.x() - (double)job.x, dz = match.z() - (double)job.z;
        String direction = (dz < 0 ? "N" : dz > 0 ? "S" : "") + (dx < 0 ? "W" : dx > 0 ? "E" : "");
        var coordinates = Component.literal("[" + match.x() + ", " + match.y() + ", " + match.z() + "]")
                .withStyle(style -> style.withColor(ChatFormatting.AQUA)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                        "/tp @s " + match.x() + " " + match.y() + " " + match.z())));
        var details = text("result_details", match.archetype(), match.exterior(), match.interior(),
                match.biome(), match.shoreline(), match.fringe(), match.family(), match.id());
        return text("result", coordinates, Math.round(Math.sqrt(match.distanceSquared())),
                direction, match.exterior().equals("native") ? match.archetype() : match.archetype() + " / " + match.exterior())
                .copy().withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, details)));
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
                case "shoreline" -> Set.of("none", "native", "coastal", "perimeter").contains(value);
                case "fringe" -> Set.of("none", "mixed", "disintegrating").contains(value);
                case "biome" -> net.minecraft.resources.ResourceLocation.tryParse(value) != null && value.contains(":");
                default -> false;
            };
            if (!valid) throw new IllegalArgumentException("Unsupported filter: " + token);
            if (key.equals("family") && value.equals("any")) filters.remove(key); else filters.put(key, value);
        }
        if (Set.of("coastal", "perimeter").contains(filters.getOrDefault("shoreline", "none"))
                && filters.containsKey("archetype") && !filters.get("archetype").equals("lagoon"))
            throw new IllegalArgumentException("Coastal/perimeter shoreline filters require lagoon");
        return Map.copyOf(filters);
    }
    private static String value(Match match, String key) {
        return switch (key) {
            case "archetype" -> match.archetype(); case "exterior" -> match.exterior();
            case "interior" -> match.interior(); case "biome" -> match.biome();
            case "shoreline" -> match.shoreline(); case "fringe" -> match.fringe(); case "family" -> match.family(); default -> throw new AssertionError(key);
        };
    }
    private static List<String> suggestions() {
        var values = new ArrayList<String>();
        for (var value : IslandShapeArchetype.values()) values.add("archetype=" + name(value));
        for (var value : IslandStyles.Kind.values()) values.add("exterior=" + name(value));
        for (var value : IslandInteriors.Kind.values()) values.add("interior=" + name(value));
        for (var value : IslandFamily.values()) values.add("family=" + name(value));
        values.addAll(List.of("shoreline=native", "shoreline=coastal", "shoreline=perimeter", "shoreline=none", "family=any", "biome=minecraft:"));
        values.addAll(List.of("fringe=none", "fringe=mixed", "fringe=disintegrating"));
        return values;
    }
    private static String name(Enum<?> value) { return value.name().toLowerCase(Locale.ROOT); }
    private static Component text(String key, Object... args) {
        return LocatorText.text("islands", key, args);
    }
    private static int fail(CommandSourceStack source, String key) { source.sendFailure(text(key)); return 0; }
    private IslandLocator() {}
}
