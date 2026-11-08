package design.inherent.skyweavecompat.terrain.structure;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import design.inherent.skyweavecompat.Feature;
import design.inherent.skyweavecompat.OptionalIntegrations;
import design.inherent.skyweavecompat.terrain.*;
import java.util.*;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import org.sathrek.sky_archipelago.config.SkyIslandSettings;
import org.sathrek.sky_archipelago.worldgen.structure.StructurePlacementCategory;
import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;
import org.sathrek.sky_archipelago.worldgen.generator.field.model.IslandDescriptor;
import org.sathrek.sky_archipelago.worldgen.generator.structure.StructurePlacementPassContext;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement.FcfsArbiter;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement.ReservationStore;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement.OverlapPolicy3D;

/** One immutable policy and bounded, reproducible plans per generator state. No chunk or saved-world access. */
public final class StructureDistribution {
    public static final int SAMPLE_SPACING = 8;
    public static final int SAMPLE_SEPARATION = 4;
    public static final int SAMPLE_SALT = 0x53575354;
    private final RandomSpreadStructurePlacement lattice = new RandomSpreadStructurePlacement(
            SAMPLE_SPACING, SAMPLE_SEPARATION, RandomSpreadType.LINEAR, SAMPLE_SALT);
    private final SkyIslandChunkGenerator generator;
    private final IslandField field;
    private final SkyIslandSettings settings;
    private final BiomeSource biomes;
    private final RandomState random;
    private final long seed;
    private final StructurePolicy policy;
    private final Map<StructureFamily, Set<Holder<Biome>>> allowedBiomes;
    private final Map<StructureFamily, List<StructureSet.StructureSelectionEntry>> familyEntries;
    private final Set<StructureSet.StructureSelectionEntry> managedEntries;
    private final Map<ResourceLocation, StructureFamily> families;
    private final Map<ResourceLocation, StructurePlacementCategory> categories;
    private final Map<String, Boolean> yungReplacements;
    private final Map<ResourceLocation, net.minecraft.world.level.levelgen.structure.placement.StructurePlacement> yungTemplePlacements;
    private YungTempleRules yungTempleRules;
    private final Cache<StructureSelection.Owner, Plan> plans = CacheBuilder.newBuilder().maximumSize(512).build();
    private final Cache<StructureSelection.Owner, Plan> fittedPlans = CacheBuilder.newBuilder().maximumSize(512).build();
    private final Cache<StructureSelection.Owner, Map<StructureSelection.Site, Sample>> sampledSites = CacheBuilder.newBuilder().maximumSize(512).build();
    private record ChoiceKey(StructureSelection.Owner owner, StructureFamily family) {}
    private record Choice(double area, int count, List<StructureSelection.Site> ranked) {}
    private final Cache<ChoiceKey, Choice> choices = CacheBuilder.newBuilder().maximumSize(4096).build();
    private volatile StructurePlacementPassContext environment;
    private final ReservationStore reservationStore = new ReservationStore();
    private final FcfsArbiter reservations = new FcfsArbiter(reservationStore, reservationStore, new OverlapPolicy3D());
    private final Set<Object> physicalClaims = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public FcfsArbiter reservations() { return reservations; }
    public Object claimPhysical(Object key) { return physicalClaims.add(key) ? null : key; }
    public void releasePhysical(Object key) { physicalClaims.remove(key); }

    public record Plan(StructureSelection.Owner owner, Map<StructureFamily, Double> eligibleChunks,
            Map<StructureFamily, List<StructureSelection.Site>> sites,
            Map<StructureFamily, List<StructureLayout>> layouts) {
        public Plan {
            eligibleChunks = Map.copyOf(eligibleChunks);
            sites = sites.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> List.copyOf(e.getValue())));
            layouts = layouts.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> List.copyOf(e.getValue())));
        }
        public List<StructureSelection.Site> sites(StructureFamily family) { return sites.getOrDefault(family, List.of()); }
        public StructureLayout layout(StructureFamily family, StructureSelection.Site site) {
            return layouts.getOrDefault(family, List.of()).stream().filter(layout -> layout.site().equals(site)).findFirst().orElse(null);
        }
    }

    public StructureDistribution(SkyIslandChunkGenerator generator, IslandField field, SkyIslandSettings settings,
            BiomeSource biomes, RandomState random, long seed, Collection<Holder<StructureSet>> sets, StructurePolicy policy) {
        this.generator = generator; this.field = field; this.settings = settings;
        this.biomes = biomes; this.random = random; this.seed = seed; this.policy = policy;
        yungReplacements = OptionalIntegrations.enabled(Feature.YUNG_STRUCTURES)
                ? ((YungWorldPolicy)(Object)generator).skyweave$yungReplacements() : Map.of();
        var allowed = new EnumMap<StructureFamily, Set<Holder<Biome>>>(StructureFamily.class);
        var entries = new EnumMap<StructureFamily, List<StructureSet.StructureSelectionEntry>>(StructureFamily.class);
        var managed = Collections.newSetFromMap(new IdentityHashMap<StructureSet.StructureSelectionEntry, Boolean>());
        var resolvedFamilies = new HashMap<ResourceLocation, StructureFamily>();
        var resolvedCategories = new HashMap<ResourceLocation, StructurePlacementCategory>();
        var templePlacements = new HashMap<ResourceLocation, net.minecraft.world.level.levelgen.structure.placement.StructurePlacement>();
        for (var holder : sets) {
            var family = managedFamily(holder.value(), settings);
            if (family == null) continue;
            managed.addAll(holder.value().structures());
            for (var entry : holder.value().structures()) {
                var id = entry.structure().unwrapKey().orElseThrow().location();
                if (StructureMembership.family(entry.structure()) == null) continue; // Nether-only portal companion.
                resolvedFamilies.put(id, family);
                resolvedCategories.put(id, StructureMembership.category(entry.structure(), family, settings.advanced().structurePlacementPolicy()));
                if (OptionalIntegrations.enabled(Feature.YUNG_STRUCTURES) && YungTempleRules.supported(holder.value().placement()))
                    templePlacements.put(id, holder.value().placement());
            }
            entries.computeIfAbsent(family, ignored -> new ArrayList<>()).addAll(holder.value().structures());
            for (var entry : holder.value().structures()) entry.structure().value().biomes().forEach(biome ->
                    allowed.computeIfAbsent(family, ignored -> new HashSet<>()).add(biome));
        }
        var immutable = new EnumMap<StructureFamily, Set<Holder<Biome>>>(StructureFamily.class);
        allowed.forEach((family, values) -> immutable.put(family, Set.copyOf(values)));
        allowedBiomes = Map.copyOf(immutable);
        entries.replaceAll((family, values) -> List.copyOf(values));
        familyEntries = Map.copyOf(entries);
        managedEntries = Collections.unmodifiableSet(managed);
        families = Map.copyOf(resolvedFamilies);
        categories = Map.copyOf(resolvedCategories);
        yungTemplePlacements = Map.copyOf(templePlacements);
    }

    public Map<String, Boolean> yungReplacements() { return yungReplacements; }
    public boolean suppresses(net.minecraft.world.level.levelgen.structure.Structure structure) {
        return YungReplacements.suppresses(yungReplacements, structure);
    }

    /** The runtime wrapper and attempt ownership must agree, including explicit city opt-outs and ring placements. */
    public static StructureFamily managedFamily(StructureSet set, SkyIslandSettings settings) {
        if (!settings.advanced().customStructureRulesEnabled()) return null;
        if (!(set.placement() instanceof RandomSpreadStructurePlacement)) return null;
        var memberships = new HashMap<String, StructureFamily>();
        for (var entry : set.structures()) memberships.put(entry.structure().unwrapKey()
                .map(key -> key.location().toString()).orElse("unregistered"), StructureMembership.family(entry.structure()));
        var family = StructureFamily.forSet(memberships.keySet(), memberships::get);
        if (family == null || family == StructureFamily.ANCIENT_CITIES && !OptionalIntegrations.enabled(Feature.ANCIENT_CITIES)) return null;
        for (var entry : set.structures()) {
            var id = entry.structure().unwrapKey().orElseThrow().location();
            // Keep explicit native bypasses and unsupported category changes under their original owner.
            if (settings.advanced().structureWhitelist().isWhitelisted(id)) return null;
            if (memberships.get(id.toString()) != null
                    && !supportsRoute(family, StructureMembership.category(entry.structure(), family, settings.advanced().structurePlacementPolicy()))) return null;
        }
        return family;
    }

    static boolean supportsRoute(StructureFamily family,
            StructurePlacementCategory category) {
        return switch (family) {
            case VILLAGES -> category == StructurePlacementCategory.GROUND_VILLAGE;
            case ANCIENT_CITIES, MINESHAFTS, TRIAL_CHAMBERS -> category == StructurePlacementCategory.UNDERGROUND;
            case TRAIL_RUINS -> category == StructurePlacementCategory.DEFAULT
                    || category == StructurePlacementCategory.UNDERGROUND;
            case DESERT_TEMPLES, JUNGLE_TEMPLES, SWAMP_HUTS, IGLOOS, MANSIONS -> category == StructurePlacementCategory.DEFAULT;
            case PORTALS, OUTPOSTS, SHIPWRECKS -> switch (category) {
                case DEFAULT, SMALL_GROUND, MEDIUM_GROUND, LARGE_GROUND, WATER -> true;
                default -> false;
            };
        };
    }

    /** Capture read-only generation inputs before native opportunity tests; never retain the live chunk. */
    public synchronized void bind(StructurePlacementPassContext context) {
        if (environment != null) return;
        yungTempleRules = YungTempleRules.capture(yungTemplePlacements, context.registryAccess());
        var height = LevelHeightAccessor.create(context.chunk().getMinBuildHeight(), context.chunk().getHeight());
        var empty = new ProtoChunk(new ChunkPos(0, 0), UpgradeData.EMPTY, height,
                context.registryAccess().registryOrThrow(Registries.BIOME), null);
        environment = new StructurePlacementPassContext(context.generator(), context.biomeSource(), context.registryAccess(),
                context.structureState(), null, empty, context.templateManager(), context.settings(), context.islandField(),
                context.levelSeed(), context.randomState(), empty.getPos(), SectionPos.bottomOf(empty));
    }

    public void bind(RegistryAccess registries, StructureTemplateManager templates, LevelHeightAccessor height,
            ChunkGeneratorStructureState state) {
        var empty = new ProtoChunk(new ChunkPos(0, 0), UpgradeData.EMPTY, height, registries.registryOrThrow(Registries.BIOME), null);
        bind(new StructurePlacementPassContext(generator, biomes, registries, state, null, empty, templates, settings, field,
                seed, random, empty.getPos(), SectionPos.bottomOf(empty)));
    }

    public StructureSelection.Owner owner(int x, int z) {
        // Cluster ownership precedes hydrology. Completed snapshots can synchronously
        // build whole watersheds even for an unselected native /locate candidate.
        var descriptor = ((InteriorField)(Object)field)
                .skyweave$planningColumn(x, z, settings, TerrainStage.EXTERIOR).dominantDescriptor();
        if (descriptor == null) return null;
        var column = ((InteriorField)(Object)field).skyweave$exteriorOf(descriptor, x, z, settings);
        if (!column.exists()) return null;
        return owner(descriptor);
    }

    public StructureSelection.Owner owner(IslandDescriptor descriptor) {
        var cell = ((ClusterField)(Object)field).skyweave$owner(descriptor, settings);
        return new StructureSelection.Owner(cell.x(), cell.z());
    }

    public Plan plan(StructureSelection.Owner owner) {
        var context = environment;
        var cache = context == null ? plans : fittedPlans;
        try { return cache.get(owner, () -> compute(owner, context)); }
        catch (java.util.concurrent.ExecutionException failed) { throw new IllegalStateException("Cannot plan cluster structures", failed.getCause()); }
    }

    public boolean manages(StructureSet.StructureSelectionEntry entry) { return managedEntries.contains(entry); }
    public StructureFamily family(ResourceLocation id) { return families.get(id); }

    /** Conservative precheck only: no biome projection, hydrology or layout fitting. Saved starts bypass it. */
    public boolean possibleNewStart(StructureFamily family, int chunkX, int chunkZ) {
        if (family == null) return true;
        var rule = policy.rule(family);
        if (policy.frequencyMultiplier() == 0 || rule.maximumPerCluster() == 0 || rule.targetStartsPer1024Chunks() == 0) return false;
        return owner(chunkX * 16 + 8, chunkZ * 16 + 8) != null;
    }
    public StructurePlacementCategory category(ResourceLocation id, Object policyOwner) {
        return settings.advanced().structurePlacementPolicy() == policyOwner ? categories.get(id) : null;
    }

    public static boolean hasLayoutPlan(StructureFamily family) {
        return family != null;
    }

    public boolean permitsVariant(ResourceLocation id, int chunkX, int chunkZ) {
        var family = families.get(id);
        // The shared vanilla portal set also contains the Nether entry; it is not an Overworld candidate.
        if (family == null) return false;
        if (environment == null || !hasLayoutPlan(family)) return true;
        var owner = owner(chunkX * 16 + 8, chunkZ * 16 + 8);
        if (owner == null) return false;
        var layout = plan(owner).layout(family, new StructureSelection.Site(chunkX, chunkZ));
        return layout != null && id.equals(layout.id());
    }

    public boolean selected(StructureFamily family, int chunkX, int chunkZ) {
        var owner = owner(chunkX * 16 + 8, chunkZ * 16 + 8);
        return owner != null && plan(owner).sites(family).contains(new StructureSelection.Site(chunkX, chunkZ));
    }

    /** Never turn a provisional, pre-binding fit into a cached absence during native lookup. */
    public boolean selectedForLookup(StructureFamily family, int chunkX, int chunkZ) {
        var owner = owner(chunkX * 16 + 8, chunkZ * 16 + 8);
        if (owner == null) return false;
        if (environment == null) return true;
        var site = new StructureSelection.Site(chunkX, chunkZ);
        var choice = choice(owner, family);
        // Fitting cannot select beyond this same ranked prefix. Reject impossible family/site
        // pairs before fitting every earlier family merely to answer an absent-city lookup.
        int index = choice.ranked().indexOf(site);
        return index >= 0 && index < StructureSelection.attemptLimit(family, choice.count()) && plan(owner).sites(family).contains(site);
    }

    private Choice choice(StructureSelection.Owner owner, StructureFamily family) {
        try { return choices.get(new ChoiceKey(owner, family), () -> {
            var samples = sampledSites.get(owner, () -> {
                var collected = new LinkedHashMap<StructureSelection.Site, Sample>();
                for (var member : ((ClusterField)(Object)field).skyweave$members(owner.cellX(), owner.cellZ(), settings))
                    sample(member, collected);
                return Map.copyOf(collected);
            });
            var candidates = samples.entrySet().stream().filter(entry -> eligible(family, entry.getKey(), entry.getValue()))
                    .map(Map.Entry::getKey).toList();
            double area = (double)candidates.size() * SAMPLE_SPACING * SAMPLE_SPACING;
            return new Choice(area, StructureSelection.allowance(seed, owner, family, area, policy),
                    StructureSelection.ranked(seed, owner, family, candidates));
        }); } catch (java.util.concurrent.ExecutionException failed) {
            throw new IllegalStateException("Cannot select cluster structure candidates", failed.getCause());
        }
    }

    private Plan compute(StructureSelection.Owner owner, StructurePlacementPassContext context) {
        var areas = new EnumMap<StructureFamily, Double>(StructureFamily.class);
        var selected = new EnumMap<StructureFamily, List<StructureSelection.Site>>(StructureFamily.class);
        var fitted = new EnumMap<StructureFamily, List<StructureLayout>>(StructureFamily.class);
        for (var family : StructureFamily.values()) {
            var choice = choice(owner, family);
            areas.put(family, choice.area());
            int count = choice.count();
            var ranked = choice.ranked();
            if (family == StructureFamily.VILLAGES && context != null) {
                var layouts = new ArrayList<StructureLayout>();
                for (var site : ranked.stream().limit(StructureSelection.attemptLimit(family, count)).toList()) {
                    if (layouts.size() == count) break;
                    var layout = VillageLayoutPlanning.fit(this, context, site, familyEntries.getOrDefault(family, List.of()));
                    if (layout == null || conflicts(layout, layouts, fitted)) continue;
                    layouts.add(layout);
                }
                selected.put(family, layouts.stream().map(StructureLayout::site).toList());
                fitted.put(family, List.copyOf(layouts));
            } else if (hasLayoutPlan(family) && context != null) {
                var layouts = new ArrayList<StructureLayout>();
                var villages = selected.getOrDefault(StructureFamily.VILLAGES, List.of());
                for (var site : ranked.stream().limit(StructureSelection.attemptLimit(family, count)).toList()) {
                    if (layouts.size() == count) break;
                    if (family == StructureFamily.OUTPOSTS && StructureSelection.outpostExcluded(site, villages, policy.outpostVillageSeparationChunks())) continue;
                    var layout = underground(family) || family == StructureFamily.TRAIL_RUINS
                            ? UndergroundLayoutPlanning.fit(this, context, family, site, familyEntries.getOrDefault(family, List.of()))
                            : GroundLayoutPlanning.fit(this, context, family, site, familyEntries.getOrDefault(family, List.of()));
                    if (layout == null || !yungTempleRules.allows(layout, layouts, fitted, context)
                            || conflicts(layout, layouts, fitted)) continue;
                    layouts.add(layout);
                }
                selected.put(family, layouts.stream().map(StructureLayout::site).toList());
                fitted.put(family, List.copyOf(layouts));
            } else selected.put(family, ranked.stream().limit(count).toList());
        }
        // Destination anchors, not ordinary-village source opportunities in void.
        var villages = selected.getOrDefault(StructureFamily.VILLAGES, List.of());
        selected.computeIfPresent(StructureFamily.OUTPOSTS, (family, candidates) -> candidates.stream()
                .filter(site -> !StructureSelection.outpostExcluded(site, villages, policy.outpostVillageSeparationChunks())).toList());
        return new Plan(owner, areas, selected, fitted);
    }

    private static boolean conflicts(StructureLayout layout, List<StructureLayout> sameFamily,
            Map<StructureFamily, List<StructureLayout>> earlierFamilies) {
        return sameFamily.stream().anyMatch(layout::conflicts)
                || earlierFamilies.values().stream().flatMap(Collection::stream).anyMatch(layout::conflicts);
    }

    /** Unknown or foreign geometry retains native reservation policy. */
    public Boolean layoutConflict(StructureSelection.Site source, StructureVolume.Box first, StructureVolume.Box second) {
        var owner = owner(source.chunkX() * 16 + 8, source.chunkZ() * 16 + 8);
        if (owner == null || environment == null) return null;
        var layouts = plan(owner).layouts().values().stream().flatMap(Collection::stream).toList();
        var a = layouts.stream().filter(value -> value.matches(first)).findFirst().orElse(null);
        var b = layouts.stream().filter(value -> value.matches(second)).findFirst().orElse(null);
        return a == null || b == null ? null : a.conflicts(b);
    }

    private void sample(IslandDescriptor member, Map<StructureSelection.Site, Sample> samples) {
        int radius = member.maxRadius();
        int minX = Math.floorDiv(Math.floorDiv(member.centerX() - radius, 16), SAMPLE_SPACING);
        int maxX = Math.floorDiv(Math.floorDiv(member.centerX() + radius, 16), SAMPLE_SPACING);
        int minZ = Math.floorDiv(Math.floorDiv(member.centerZ() - radius, 16), SAMPLE_SPACING);
        int maxZ = Math.floorDiv(Math.floorDiv(member.centerZ() + radius, 16), SAMPLE_SPACING);
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
            ChunkPos chunk = lattice.getPotentialStructureChunk(seed, x * SAMPLE_SPACING, z * SAMPLE_SPACING);
            int bx = chunk.getMiddleBlockX(), bz = chunk.getMiddleBlockZ();
            var column = ((InteriorField)(Object)field).skyweave$exteriorOf(member, bx, bz, settings);
            if (!column.exists()) continue;
            var site = new StructureSelection.Site(chunk.x, chunk.z);
            var previous = samples.get(site);
            if (previous == null || previous.topY < column.topY()) samples.put(site, new Sample(column.topY(), member));
        }
    }

    private boolean eligible(StructureFamily family, StructureSelection.Site site, Sample sample) {
        var allowed = allowedBiomes.get(family);
        if (allowed == null) return false;
        if (family == StructureFamily.ANCIENT_CITIES && !OptionalIntegrations.enabled(Feature.ANCIENT_CITIES)) return false;
        int y = sample.topY + 1;
        if (underground(family)) y = IslandGeometry.geologicalFrame(sample.member).toWorld(-27);
        int qx = site.chunkX() * 4 + 2, qz = site.chunkZ() * 4 + 2, qy = Math.floorDiv(y, 4);
        var biome = biomes.getNoiseBiome(qx, qy, qz, random.sampler());
        if (underground(family) && OptionalIntegrations.enabled(Feature.CAVE_BIOMES))
            biome = CaveProjection.physical(generator, random, biomes, biome, qx, qy, qz, random.sampler());
        return allowed.contains(biome);
    }

    public static boolean underground(StructureFamily family) {
        return family == StructureFamily.ANCIENT_CITIES || family == StructureFamily.MINESHAFTS || family == StructureFamily.TRIAL_CHAMBERS;
    }
    private record Sample(int topY, IslandDescriptor member) {}
}
