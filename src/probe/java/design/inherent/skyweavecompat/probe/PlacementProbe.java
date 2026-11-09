package design.inherent.skyweavecompat.probe;

import com.google.gson.*;
import design.inherent.skyweavecompat.NeighborConfig;
import design.inherent.skyweavecompat.placement.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;
import org.sathrek.sky_archipelago.worldgen.structure.*;
import java.nio.file.*;
import java.util.*;

@Mod("skyweave_compat_probe")
public final class PlacementProbe {
    private static final ResourceLocation VILLAGE = ResourceLocation.fromNamespaceAndPath("skyvillages", "skyvillage");
    private int settleTicks;
    private boolean asynchronous;
    private net.minecraft.server.MinecraftServer pendingServer;
    public PlacementProbe() {
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerAboutToStartEvent event) -> {
            // Structure templates must exist before spawn generation can cache an empty template.
            try { StructureLocatorProbe.prepare(event.getServer()); }
            catch (Exception failure) { throw new IllegalStateException("Cannot prepare locator fixture", failure); }
        });
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> pendingServer = event.getServer());
        NeoForge.EVENT_BUS.addListener(this::settle);
    }
    private void settle(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        WaterProbe.tick(event.getServer());
        LocatorProbe.tick(event.getServer());
        HydrologyProbe.tick(event.getServer());
        StructureLocatorProbe.tick(event.getServer());
        LocatorLifecycleProbe.tick(event.getServer());
        // Sky Archipelago chooses its fallback spawn in another ServerStarted listener.
        // The first completed tick observes all startup handlers, regardless of registration order.
        if (pendingServer != null) {
            var server = pendingServer;
            pendingServer = null;
            run(server);
            return;
        }
        // Assembly removal and physics cleanup happen during ticks, not ServerStartedEvent.
        if (settleTicks > 0 && --settleTicks == 0) {
            try { StructureCoverageProbe.finish(event.getServer()); }
            catch (Exception error) { throw new RuntimeException("Structure coverage completion failed", error); }
            event.getServer().halt(false);
        }
    }
    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }
    private static void setPolicy(NeighborRule.Settings settings) throws Exception {
        var field = NeighborConfig.class.getDeclaredField("active");
        field.setAccessible(true);
        field.set(null, settings);
    }
    private void natural(net.minecraft.server.MinecraftServer server,
            net.minecraft.world.level.levelgen.structure.Structure structure) throws Exception {
        var level = server.overworld();
        var generator = (SkyIslandChunkGenerator)level.getChunkSource().getGenerator();
        var settings = generator.resolvedSkyIslandSettings();
        var getter = generator.getClass().getDeclaredMethod("getIslandField", RandomState.class);
        getter.setAccessible(true);
        var field = (IslandField)getter.invoke(generator, level.getChunkSource().randomState());
        var terrain = new IslandTerrain(new StructureSupportContext(VILLAGE, settings, field));
        var holder = server.registryAccess().registryOrThrow(Registries.STRUCTURE).wrapAsHolder(structure);
        var placement = (net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement)
                level.getChunkSource().getGeneratorState().getPlacementsForStructure(holder).getFirst();
        var planned = placement instanceof ArchipelagoVillagePlacement adapted
                ? VillagePlanProbe.run(level, field, settings, adapted) : null;
        if (planned != null) System.out.println("VILLAGE_PLAN targets=" + planned.get("targetHistogram") + " planned=" + planned.get("plannedHistogram"));
        var ownerCounts = new HashMap<VillagePlan.Anchor, Integer>();
        JsonArray rows = new JsonArray(); int accepted = 0, completed = 0;
        int radius = placement instanceof ArchipelagoVillagePlacement ? 12 : 3;
        var candidates = new LinkedHashSet<ChunkPos>();
        for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
            candidates.add(placement.getPotentialStructureChunk(level.getSeed(), x * placement.spacing(), z * placement.spacing()));
        }
        // Exercise every planned site in the broader descriptor survey, including the rare three-site anchors.
        if (planned != null) for (var anchorRow : planned.getAsJsonArray("rows")) {
            for (var siteRow : anchorRow.getAsJsonObject().getAsJsonArray("sites")) {
                var site = siteRow.getAsJsonObject();
                candidates.add(new ChunkPos(site.get("chunkX").getAsInt(), site.get("chunkZ").getAsInt()));
            }
        }
        for (var candidate : candidates) {
            var chunk = level.getChunk(candidate.x, candidate.z, net.minecraft.world.level.chunk.status.ChunkStatus.STRUCTURE_STARTS);
            var start = chunk.getStartForStructure(structure);
            JsonObject row = new JsonObject(); row.addProperty("x", candidate.x); row.addProperty("z", candidate.z);
            boolean valid = start != null && start.isValid(); row.addProperty("accepted", valid);
            if (!valid && placement instanceof ArchipelagoVillagePlacement adapted
                    && placement.isStructureChunk(level.getChunkSource().getGeneratorState(), candidate.x, candidate.z)) {
                var preview = structure.generate(server.registryAccess(), generator, generator.getBiomeSource(),
                        level.getChunkSource().randomState(), server.getStructureManager(), level.getSeed(), candidate, 0, level,
                        biome -> structure.biomes().contains(biome));
                row.addProperty("selected", true); row.addProperty("layoutValid", preview.isValid());
                if (!preview.isValid()) {
                    var unchecked = structure.generate(server.registryAccess(), generator, generator.getBiomeSource(),
                            level.getChunkSource().randomState(), server.getStructureManager(), level.getSeed(), candidate, 0, level,
                            biome -> true);
                    row.addProperty("layoutWithoutBiomeFilter", unchecked.isValid());
                    var context = new net.minecraft.world.level.levelgen.structure.Structure.GenerationContext(
                            server.registryAccess(), generator, generator.getBiomeSource(), level.getChunkSource().randomState(),
                            server.getStructureManager(), level.getSeed(), candidate, level, biome -> true);
                    structure.findValidGenerationPoint(context).ifPresent(stub -> {
                        var p = stub.position();
                        row.addProperty("biome", generator.getBiomeSource().getNoiseBiome(p.getX() >> 2, p.getY() >> 2,
                                p.getZ() >> 2, level.getChunkSource().randomState().sampler()).getRegisteredName());
                        row.addProperty("stubY", p.getY());
                    });
                }
                if (preview.isValid()) {
                    var bb = preview.getBoundingBox();
                    row.addProperty("neighborDecision", NeighborRule.evaluate(
                            new NeighborRule.Footprint(bb.minX(), bb.minZ(), bb.maxX(), bb.maxZ()), NeighborConfig.settings(), terrain).name());
                    row.addProperty("nativeClearance", new StructureSupportValidator((a, b, c) -> { throw new AssertionError("Grounding called"); })
                            .evaluatePlacement(new StructureSupportContext(VILLAGE, settings, field), preview, StructurePlacementCategory.SKY).accepted());
                }
            }
            if (valid) {
                accepted++;
                if (placement instanceof ArchipelagoVillagePlacement adapted) {
                    var owner = adapted.owner(new VillagePlan.Site(candidate.x, candidate.z));
                    int count = ownerCounts.merge(owner, 1, Integer::sum);
                    if (count > VillageDistribution.forAnchor(owner.seed())) throw new AssertionError("Realized village target exceeded");
                    row.add("anchor", new Gson().toJsonTree(owner));
                }
                var b = start.getBoundingBox();
                row.addProperty("minY", b.minY()); row.addProperty("maxY", b.maxY());
                row.addProperty("width", b.getXSpan()); row.addProperty("depth", b.getZSpan());
                if (placement instanceof ArchipelagoVillagePlacement adapted) {
                    var actual = adapted.owner((b.minX() + b.maxX()) / 2, (b.minZ() + b.maxZ()) / 2);
                    check(adapted.owner(new VillagePlan.Site(candidate.x, candidate.z)).equals(actual),
                            "Native layout crossed anchor ownership: source=" + candidate + " layout=" + b + " actual=" + actual);
                }
                check(NeighborRule.evaluate(new NeighborRule.Footprint(b.minX(), b.minZ(), b.maxX(), b.maxZ()),
                        NeighborConfig.settings(), terrain) == NeighborRule.Decision.ACCEPTED, "Natural start violates placement rule");
                check(b.minY() >= level.getMinBuildHeight() && b.maxY() < level.getMaxBuildHeight(), "Vertical bounds");
                if (placement instanceof ArchipelagoVillagePlacement adapted) {
                    int surface = adapted.owner(new VillagePlan.Site(candidate.x, candidate.z)).surfaceY();
                    row.addProperty("hostSurfaceY", surface);
                    // Retain the tested layout envelope, translated with the host instead of fixed at Y=508.
                    check(b.minY() >= surface - 108 && b.maxY() <= surface + 142,
                            "Village did not follow host altitude: surface=" + surface + " layout=" + b);
                }
                if (completed < 2) {
                    for (int cx = b.minX() >> 4; cx <= b.maxX() >> 4; cx++)
                        for (int cz = b.minZ() >> 4; cz <= b.maxZ() >> 4; cz++) level.getChunk(cx, cz);
                    int nonAir = 0;
                    var blockCounts = new TreeMap<String, Integer>();
                    var pos = new net.minecraft.core.BlockPos.MutableBlockPos();
                    for (int bx = b.minX(); bx <= b.maxX(); bx++) for (int bz = b.minZ(); bz <= b.maxZ(); bz++)
                        for (int by = b.minY(); by <= b.maxY(); by++) {
                            var state = level.getBlockState(pos.set(bx, by, bz));
                            if (!state.isAir()) {
                                nonAir++;
                                blockCounts.merge(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(), 1, Integer::sum);
                            }
                        }
                    check(nonAir > 100, "Registered layout has no realized structure");
                    row.addProperty("blocks", nonAir); row.addProperty("pieces", start.getPieces().size());
                    row.add("blockCounts", new Gson().toJsonTree(blockCounts));
                    row.add("lootSamples", VillageContentProbe.inspect(level, b));
                    var located = generator.findNearestMapStructure(level, net.minecraft.core.HolderSet.direct(holder),
                            candidate.getMiddleBlockPosition(508), 2, false);
                    check(located != null && located.getSecond().equals(holder), "Native locate lost the village placement");
                    row.addProperty("locateFound", true);
                    if (((design.inherent.skyweavecompat.terrain.ProfiledTerrain)(Object)field).skyweave$profile() != null) {
                        row.add("delivery", DeliveryProbe.run(level, located.getFirst(), b));
                    }
                    completed++;
                }
            }
            rows.add(row);
        }
        System.out.println("VILLAGE_STARTS accepted=" + accepted + " completed=" + completed + " queried=" + rows.size());
        JsonObject report = new JsonObject(); report.addProperty("seed", level.getSeed());
        report.add("settings", new Gson().toJsonTree(NeighborConfig.settings()));
        report.addProperty("accepted", accepted); report.addProperty("completed", completed); report.add("samples", rows);
        if (planned != null) {
            int[] realized = new int[4];
            for (var anchorRow : planned.getAsJsonArray("rows")) {
                var anchor = new Gson().fromJson(anchorRow.getAsJsonObject().get("anchor"), VillagePlan.Anchor.class);
                int count = ownerCounts.getOrDefault(anchor, 0);
                if (count > VillageDistribution.forAnchor(anchor.seed())) throw new AssertionError("Realized allowance exceeded");
                realized[count]++;
            }
            planned.add("realizedHistogram", new Gson().toJsonTree(realized));
            report.add("villagePlan", planned);
        }
        report.addProperty("complete", accepted > 0 && completed > 0);
        Files.writeString(Path.of(System.getProperty("neighbors.probe.output")), new GsonBuilder().setPrettyPrinting().create().toJson(report));
        check(accepted > 0 && completed > 0, "No full natural villages exercised");
        System.out.println("NEIGHBORS_NATURAL_COMPLETE " + accepted + " starts; " + completed + " complete villages");
    }

    private void run(net.minecraft.server.MinecraftServer server) {
        try {
            if (Boolean.getBoolean("neighbors.probe.quests")) {
                QuestBehaviorProbe.run(server, Path.of(System.getProperty("neighbors.probe.output")));
                return;
            }
            if (Boolean.getBoolean("neighbors.probe.access")) {
                AccessProbe.run(server, Path.of(System.getProperty("neighbors.probe.output")));
                return;
            }
            if (Boolean.getBoolean("neighbors.probe.structureSearch")) {
                StructureLocatorProbe.start(server, Path.of(System.getProperty("neighbors.probe.output")));
                asynchronous = true; return;
            }
            if (Boolean.getBoolean("neighbors.probe.perimeter")) {
                if (Boolean.getBoolean("neighbors.probe.oceanFringes")) {
                    OceanFringeProbe.run(server, Path.of(System.getProperty("neighbors.probe.output")));
                    settleTicks = 100;
                } else FringeProbe.run(server, Path.of(System.getProperty("neighbors.probe.output")));
                return;
            }
            if (Boolean.getBoolean("neighbors.probe.hydrology")) {
                HydrologyProbe.start(server, Path.of(System.getProperty("neighbors.probe.output")), Integer.getInteger("neighbors.probe.fluidTicks", 200));
                asynchronous = true;
                return;
            }
            if (Boolean.getBoolean("neighbors.probe.audio")) {
                AnalogAudioProbe.run(server, Path.of(System.getProperty("neighbors.probe.output")));
                return;
            }
            check(NeighborConfig.settings().maximumGap() == Integer.getInteger("neighbors.probe.expectedMaximumGap", 128), "Config not applied at server start");
            if (Boolean.getBoolean("neighbors.probe.variation")) {
                VariationProbe.run(server, Path.of(System.getProperty("neighbors.probe.output")));
                return;
            }
            if (Boolean.getBoolean("neighbors.probe.locator")) {
                LocatorProbe.start(server, Path.of(System.getProperty("neighbors.probe.output")));
                asynchronous = true;
                return;
            }
            if (Boolean.getBoolean("neighbors.probe.lookup")) {
                LookupProbe.run(server, Path.of(System.getProperty("neighbors.probe.output")));
                return;
            }
            if (Boolean.getBoolean("neighbors.probe.structures")) {
                StructureCoverageProbe.run(server, Path.of(System.getProperty("neighbors.probe.output")));
                settleTicks = 100;
                return;
            }
            if (Boolean.getBoolean("neighbors.probe.coverage")) {
                CoverageProbe.run(server, Path.of(System.getProperty("neighbors.probe.output")));
                // Let asynchronous full-pack startup callbacks finish before shutting down.
                settleTicks = 100;
                return;
            }
            if (Boolean.getBoolean("neighbors.probe.benchmark")) {
                BenchmarkProbe.run(server, Path.of(System.getProperty("neighbors.probe.output")));
                return;
            }
            if (Boolean.getBoolean("neighbors.probe.overlap")) {
                OverlapProbe.run(server, Path.of(System.getProperty("neighbors.probe.output")));
                settleTicks = 100;
                return;
            }
            if (Boolean.getBoolean("neighbors.probe.interiors")) {
                InteriorProbe.run(server, Path.of(System.getProperty("neighbors.probe.output")));
                settleTicks = 100;
                return;
            }
            if (Boolean.getBoolean("neighbors.probe.veins")) {
                VeinProbe.run(server, Path.of(System.getProperty("neighbors.probe.output")));
                settleTicks = 100;
                return;
            }
            if (Boolean.getBoolean("neighbors.probe.materials")) {
                MaterialProbe.run(server, Path.of(System.getProperty("neighbors.probe.output")));
                return;
            }
            if (Boolean.getBoolean("neighbors.probe.ecology")) {
                EcologyProbe.run(server, Path.of(System.getProperty("neighbors.probe.output")));
                return;
            }
            if (Boolean.getBoolean("neighbors.probe.villagePools")) {
                VillagePoolProbe.run(server, Path.of(System.getProperty("neighbors.probe.output")));
                return;
            }
            if (!System.getProperty("neighbors.probe.yung", "").isEmpty()) {
                YungProbe.run(server, Path.of(System.getProperty("neighbors.probe.output")));
                return;
            }
            if (Boolean.getBoolean("neighbors.probe.water")) {
                asynchronous = WaterProbe.start(server, Path.of(System.getProperty("neighbors.probe.output")),
                        Integer.getInteger("neighbors.probe.fluidTicks", 600));
                return;
            }
            if (Boolean.getBoolean("neighbors.probe.dhPregen")) {
                DhPregenProbe.start(server, Path.of(System.getProperty("neighbors.probe.output")));
                asynchronous = true;
                return;
            }
            if (Boolean.getBoolean("neighbors.probe.chunkFutures")) {
                ChunkFutureProbe.start(server, Path.of(System.getProperty("neighbors.probe.output")));
                asynchronous = true;
                return;
            }
            if (Boolean.getBoolean("neighbors.probe.islands")) {
                IslandSurveyProbe.run(server, Path.of(System.getProperty("neighbors.probe.output")));
                return;
            }
            if (Boolean.getBoolean("neighbors.probe.mechanics")) {
                MechanicsProbe.run(server, Path.of(System.getProperty("neighbors.probe.output")));
                settleTicks = 40;
                return;
            }
            if (Boolean.getBoolean("neighbors.probe.surfaceClimate")) {
                SurfaceClimateProbe.run(server, Path.of(System.getProperty("neighbors.probe.output")));
                return;
            }
            if (Boolean.getBoolean("neighbors.probe.biomes")) {
                BiomeProbe.run(server, Path.of(System.getProperty("neighbors.probe.output")));
                return;
            }
            if (!System.getProperty("neighbors.probe.generation", "").isEmpty()) {
                GenerationProbe.run(server, Path.of(System.getProperty("neighbors.probe.output")));
                return;
            }
            var preset = server.registryAccess().registryOrThrow(Registries.WORLD_PRESET)
                    .get(ResourceLocation.fromNamespaceAndPath("sky_archipelago", "sky_islands"));
            var generator = (SkyIslandChunkGenerator)preset.createWorldDimensions().overworld();
            var settings = generator.resolvedSkyIslandSettings();
            if (Boolean.getBoolean("neighbors.probe.terrain")) {
                TerrainProbe.run(server, settings, Path.of(System.getProperty("neighbors.probe.output")));
                return;
            }
            var getter = generator.getClass().getDeclaredMethod("getIslandField", RandomState.class);
            getter.setAccessible(true);
            var validator = new StructureSupportValidator((a, b, c) -> { throw new AssertionError("Grounding called"); });
            var structure = server.registryAccess().registryOrThrow(Registries.STRUCTURE).get(VILLAGE);
            var level = server.overworld();
            var flat = level.getChunkSource().getGenerator();
            if (Boolean.getBoolean("neighbors.probe.natural")) {
                natural(server, structure);
                return;
            }
            var policies = List.of(new NeighborRule.Settings(true, 64, 384, 96),
                    new NeighborRule.Settings(true, 64, 512, 96), new NeighborRule.Settings(true, 64, 768, 96));
            JsonArray results = new JsonArray();
            int mixinRejections = 0, nativeAccepted = 0;
            for (long seed : new long[]{1, 42, 8675309, 424242}) {
                var random = RandomState.create(server.registryAccess().registryOrThrow(Registries.NOISE_SETTINGS)
                        .getOrThrow(NoiseGeneratorSettings.OVERWORLD), server.registryAccess().registryOrThrow(Registries.NOISE).asLookup(), seed);
                var field = (IslandField)getter.invoke(generator, random);
                var context = new StructureSupportContext(VILLAGE, settings, field);
                var terrain = new IslandTerrain(context);
                // Layout-only samples isolate the predicate from biome rejection and chunk visitation.
                int samples = Integer.getInteger("neighbors.probe.samples", 12);
                for (int index = 0; index < samples; index++) {
                    int x = (index % 4 - 2) * 70 + 2, z = (index / 4 - 1) * 70 + 2;
                    StructureStart start = structure.generate(server.registryAccess(), flat, flat.getBiomeSource(),
                            level.getChunkSource().randomState(), server.getStructureManager(), seed, new ChunkPos(x, z), 0, level, biome -> true);
                    check(start.isValid(), "Native layout invalid");
                    setPolicy(new NeighborRule.Settings(false, 64, 512, 96));
                    var baseline = validator.evaluatePlacement(context, start, StructurePlacementCategory.SKY);
                    if (!baseline.accepted()) continue;
                    nativeAccepted++;
                    var b = start.getBoundingBox();
                    var footprint = new NeighborRule.Footprint(b.minX(), b.minZ(), b.maxX(), b.maxZ());
                    JsonObject row = new JsonObject(); row.addProperty("seed", seed); row.addProperty("index", index);
                    row.addProperty("width", b.getXSpan()); row.addProperty("depth", b.getZSpan());
                    JsonArray variants = new JsonArray();
                    for (var policy : policies) {
                        long began = System.nanoTime();
                        var decision = NeighborRule.evaluate(footprint, policy, terrain);
                        JsonObject variant = new JsonObject(); variant.addProperty("maximumGap", policy.maximumGap());
                        variant.addProperty("decision", decision.name()); variant.addProperty("ms", (System.nanoTime() - began) / 1_000_000.0);
                        variants.add(variant);
                        if (policy.maximumGap() == 512) {
                            setPolicy(policy);
                            var patched = validator.evaluatePlacement(context, start, StructurePlacementCategory.SKY);
                            check(patched.accepted() == (decision == NeighborRule.Decision.ACCEPTED), "Mixin differs from native predicate");
                            if (!patched.accepted()) mixinRejections++;
                            check(start.getBoundingBox().equals(b), "Layout moved");
                            var otherContext = new StructureSupportContext(ResourceLocation.withDefaultNamespace("shipwreck"), settings, field);
                            var other = validator.evaluatePlacement(otherContext, start, StructurePlacementCategory.SKY);
                            check(other.accepted(), "Unrelated SKY structure filtered");
                        }
                    }
                    row.add("variants", variants); results.add(row);
                    System.out.println("NEIGHBORS_SAMPLE " + row);
                }
            }
            check(nativeAccepted > 0 && mixinRejections > 0, "Did not exercise Mixin rejection");
            JsonObject report = new JsonObject(); report.addProperty("nativeAccepted", nativeAccepted);
            report.addProperty("mixinRejections", mixinRejections); report.add("samples", results);
            Files.writeString(Path.of(System.getProperty("neighbors.probe.output")), new GsonBuilder().setPrettyPrinting().create().toJson(report));
            System.out.println("NEIGHBORS_PROBE_COMPLETE");
        } catch (Throwable exception) { exception.printStackTrace(); }
        finally { if (settleTicks == 0 && !asynchronous) server.halt(false); }
    }
}
