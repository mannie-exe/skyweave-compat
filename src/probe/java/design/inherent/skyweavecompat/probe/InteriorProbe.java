package design.inherent.skyweavecompat.probe;

import org.sathrek.sky_archipelago.worldgen.generator.field.model.IslandDescriptor;
import com.google.gson.*;
import design.inherent.skyweavecompat.terrain.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.RandomState;
import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;
import org.sathrek.sky_archipelago.worldgen.generator.field.TerrainColumn;
import java.nio.file.*;
import java.util.*;

/** Native interior sections plus independent direct/snapshot query ordering. */
final class InteriorProbe {
    private InteriorProbe() {}

    static void run(MinecraftServer server, Path output) throws Exception {
        var level = server.overworld();
        var generator = (SkyIslandChunkGenerator)level.getChunkSource().getGenerator();
        var settings = generator.resolvedSkyIslandSettings();
        var field = ((GeologicalGenerator)(Object)generator).skyweave$field(level.getChunkSource().randomState());
        var otherRandom = RandomState.create(generator.generatorSettings().value(),
                server.registryAccess().registryOrThrow(Registries.NOISE).asLookup(), level.getSeed());
        var otherGenerator = new SkyIslandChunkGenerator(generator.getBiomeSource(), generator.generatorSettings(), generator.embeddedSettings());
        var other = ((GeologicalGenerator)(Object)otherGenerator).skyweave$field(otherRandom);
        if (field == other) throw new AssertionError("Order check requires independent fields");
        var candidates = new ArrayList<IslandDescriptor>();
        for (int cx = -5; cx <= 5; cx++) for (int cz = -5; cz <= 5; cz++) {
            var members = ((ClusterField)(Object)field).skyweave$members(cx, cz, settings);
            if (!members.isEmpty()) candidates.add(members.getFirst());
        }
        candidates.sort(Comparator.comparingDouble(p -> Math.hypot(p.centerX(), p.centerZ())));
        var selected = new LinkedHashMap<IslandInteriors.Kind, IslandDescriptor>();
        for (var island : candidates) selected.putIfAbsent(IslandInteriors.kind(island, InteriorConfiguration.profile()), island);
        var rows = new JsonArray();
        long started = System.nanoTime(); long agreementChecks = 0;
        for (var entry : selected.entrySet()) {
            if (Boolean.getBoolean("neighbors.probe.skylightsOnly")) break;
            var island = entry.getValue();
            long exteriorVolume = 0, interiorVolume = 0, solidVolume = 0, waterVolume = 0, caveColumns = 0;
            long completedAdded = 0, completedRemoved = 0;
            int largestInteriorGap = 0;
            var sections = new JsonArray();
            int reach = island.maxRadius();
            for (int x = island.centerX() - reach; x <= island.centerX() + reach; x += 16)
                for (int z = island.centerZ() - reach; z <= island.centerZ() + reach; z += 16) {
                    var exterior = ((InteriorField)(Object)field).skyweave$exteriorAt(x, z, settings);
                    var planning = (InteriorField)(Object)field;
                    var interior = planning.skyweave$planningColumn(x, z, settings, TerrainStage.INTERIOR).segments();
                    var solid = field.sampleSolidSegments(x, z, settings);
                    long before = exterior.stream().mapToInt(TerrainColumn::thickness).sum();
                    long after = solid.stream().mapToInt(TerrainColumn::thickness).sum();
                    long carved = interior.stream().mapToInt(TerrainColumn::thickness).sum();
                    if (carved > before) throw new AssertionError("Interior subtraction added solid at " + x + "," + z);
                    for (var segment : interior) for (int y = segment.bottomY(); y <= segment.topY(); y++) {
                        final int sy = y;
                        if (exterior.stream().noneMatch(s -> s.contains(sy))) throw new AssertionError("Solid outside exterior");
                    }
                    if (carved < before) caveColumns++;
                    // Native interpolation, shoreline sealing and weathered lips belong to COMPLETE,
                    // not to the cavity subtraction stage. Account for their changes explicitly.
                    for (int y = GenerationProfile.MIN_Y; y < GenerationProfile.MAX_Y; y++) {
                        final int sy = y;
                        boolean rawSolid = interior.stream().anyMatch(segment -> segment.contains(sy));
                        boolean finalSolid = solid.stream().anyMatch(segment -> segment.contains(sy));
                        if (finalSolid && !rawSolid && exterior.stream().anyMatch(segment -> segment.contains(sy)))
                            throw new AssertionError("Completed stage refilled an authored cavity at " + x + "," + y + "," + z);
                        if (finalSolid && !rawSolid) completedAdded++;
                        if (rawSolid && !finalSolid) completedRemoved++;
                    }
                    exteriorVolume += before; solidVolume += after;
                    interiorVolume += interior.stream().mapToInt(TerrainColumn::thickness).sum();
                    waterVolume += WaterProbe.columns(field, x, z, settings).stream().mapToInt(TerrainColumn::thickness).sum();
                    // Sample the independent field through snapshot first, then direct column.
                    if ((x - island.centerX() + reach) % 128 == 0 && (z - island.centerZ() + reach) % 128 == 0) {
                        var context = planning.skyweave$contextAt(x,z,settings);
                        for(int sy=GenerationProfile.MIN_Y;sy<GenerationProfile.MAX_Y;sy++) {
                            final int py=sy;
                            boolean expected=interior.stream().anyMatch(span->span.contains(py));
                            if((context.solidOwner(sy)!=null)!=expected)
                                throw new AssertionError("Intrinsic point/interval disagreement at "+x+","+sy+","+z);
                            if(context.contributions(sy).stream().anyMatch(point->point.occupancy()==IslandContext.Occupancy.UNKNOWN))
                                throw new AssertionError("Unresolved native contributor at "+x+","+sy+","+z);
                        }
                        if (!planning.skyweave$planningColumn(x, z, settings, TerrainStage.EXTERIOR).segments().equals(exterior)
                                || !((InteriorField)(Object)other).skyweave$planningColumn(x, z, settings, TerrainStage.INTERIOR).segments().equals(interior))
                            throw new AssertionError("Planning stage disagrees with resolved column");
                        var snapshot = other.sampleChunkTerrainSnapshot(new ChunkPos(x >> 4, z >> 4), settings);
                        if (!solid.equals(snapshot.segmentsAt(x, z)) || !solid.equals(other.sampleSolidSegments(x, z, settings)))
                            throw new AssertionError("Snapshot/direct order changed solid intervals");
                        agreementChecks++;
                    }
                    for (var outer : exterior) {
                        int gap = 0;
                        for (int y = outer.bottomY(); y <= outer.topY(); y++) {
                            final int sy = y;
                            if (interior.stream().anyMatch(s -> s.contains(sy))) gap = 0;
                            else largestInteriorGap = Math.max(largestInteriorGap, ++gap);
                        }
                    }
                }
            for (int x = island.centerX() - reach; x <= island.centerX() + reach; x += 2) {
                var section = new JsonObject(); section.addProperty("x", x); section.addProperty("z", island.centerZ());
                section.add("exterior", new Gson().toJsonTree(((InteriorField)(Object)field).skyweave$exteriorAt(x, island.centerZ(), settings)));
                section.add("interior", new Gson().toJsonTree(((InteriorField)(Object)field)
                        .skyweave$planningColumn(x, island.centerZ(), settings, TerrainStage.INTERIOR).segments()));
                section.add("solid", new Gson().toJsonTree(field.sampleSolidSegments(x, island.centerZ(), settings)));
                section.add("water", new Gson().toJsonTree(WaterProbe.columns(field, x, island.centerZ(), settings)));
                sections.add(section);
            }
            var row = new JsonObject(); row.addProperty("kind", entry.getKey().name());
            row.add("island", new Gson().toJsonTree(island)); row.addProperty("sampleStep", 16);
            row.addProperty("exteriorSampledBlocks", exteriorVolume); row.addProperty("solidSampledBlocks", solidVolume);
            row.addProperty("interiorSampledBlocks", interiorVolume); row.addProperty("waterSampledBlocks", waterVolume);
            row.addProperty("completedNativeAddedBlocks", completedAdded); row.addProperty("completedNativeRemovedBlocks", completedRemoved);
            row.addProperty("cavityColumns", caveColumns); row.addProperty("largestInteriorGap", largestInteriorGap);
            row.add("section", sections); rows.add(row);
        }
        var report = new JsonObject(); report.addProperty("seed", Long.toString(level.getSeed()));
        report.addProperty("evidence", Boolean.getBoolean("neighbors.probe.skylightsOnly")
                ? "bounded native skylight roof and protection controls; no volume survey"
                : "native exterior/solid intervals before classic carving, features and fluid ticks");
        report.add("interiors", new Gson().toJsonTree(InteriorConfiguration.profile()));
        report.addProperty("queryOrderChecks", agreementChecks); report.add("samples", rows);
        report.add("skylights", skylights(level, field, settings, candidates));
        report.addProperty("elapsedSeconds", (System.nanoTime() - started) / 1e9); report.addProperty("complete", true);
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
    }

    private static JsonArray skylights(net.minecraft.server.level.ServerLevel level, IslandField field,
            org.sathrek.sky_archipelago.config.SkyIslandSettings settings, List<IslandDescriptor> candidates) {
        var profile = InteriorConfiguration.profile(); var rows = new JsonArray();
        var planning = (InteriorField)(Object)field;
        var styles = ((StyledTerrain)(Object)field).skyweave$styles();
        java.util.function.Predicate<IslandDescriptor> nativeHost = island ->
                styles.style(island).kind() == IslandStyles.Kind.NATIVE
                // A crescent may have no roof at its descriptor center. There is
                // nothing to puncture there; never demand new terrain for a shaft.
                && planning.skyweave$exteriorOf(island, island.centerX(), island.centerZ(), settings).exists();
        // A seed can contain no admitted HOLLOW opening in the small volume survey.
        // Extend only the cheap descriptor search; never accept a vacuous skylight pass.
        var openings = new ArrayList<>(candidates);
        for (int ring = 6; ring <= 12 && openings.stream().filter(island -> !island.archetype().waterBased()
                && nativeHost.test(island)
                && IslandInteriors.kind(island, profile) == IslandInteriors.Kind.HOLLOW
                && OceanProfile.unit(island.seed() ^ 0x329df348abc51d27L) < profile.openingChance()).count() < 2; ring++) {
            for (int x = -ring; x <= ring; x++) for (int z = -ring; z <= ring; z++) {
                if (Math.max(Math.abs(x), Math.abs(z)) != ring) continue;
                var members = ((ClusterField)(Object)field).skyweave$members(x, z, settings);
                if (!members.isEmpty()) openings.add(members.getFirst());
            }
        }
        for (var kind : List.of(IslandInteriors.Kind.HOLLOW, IslandInteriors.Kind.NOISE)) {
            var selected = openings.stream().filter(island -> !island.archetype().waterBased()
                    && nativeHost.test(island)
                    && IslandInteriors.kind(island, profile) == kind
                    && OceanProfile.unit(island.seed() ^ 0x329df348abc51d27L) < profile.openingChance()).limit(2).toList();
            if (kind == IslandInteriors.Kind.HOLLOW && profile.skylights().enabled()
                    && profile.hollowChance() > 0 && profile.openingChance() > 0 && selected.isEmpty())
                throw new AssertionError("Bounded fixture found no admitted HOLLOW skylight; coverage is inconclusive");
            for (var island : selected) {
                int open = 0, generated = 0;
                int bound = (int)Math.ceil(SkylightShape.bound(profile.skylights())) + 2;
                for (int dx = -bound; dx <= bound; dx++) for (int dz = -bound; dz <= bound; dz++) {
                    int x = island.centerX() + dx, z = island.centerZ() + dz;
                    var exterior = planning.skyweave$planningColumn(x, z, settings, TerrainStage.EXTERIOR).segments();
                    var interior = planning.skyweave$planningColumn(x, z, settings, TerrainStage.INTERIOR).segments();
                    if (exterior.isEmpty()) continue;
                    int top = exterior.getFirst().topY();
                    boolean removed = interior.stream().noneMatch(segment -> segment.contains(top));
                    if (!removed) continue;
                    open++;
                    if (!profile.skylights().enabled()) throw new AssertionError("Disabled skylights removed a roof");
                    if (kind == IslandInteriors.Kind.NOISE && !profile.skylights().noiseInteriors())
                        throw new AssertionError("NOISE roof acquired a default skylight");
                    if (!SkylightShape.contains(island.seed(), dx, dz, profile.skylights()))
                        throw new AssertionError("Skylight exceeded its declared outline");
                    int bottom = exterior.getLast().bottomY();
                    if (interior.stream().noneMatch(segment -> segment.contains(bottom))) throw new AssertionError("Skylight removed island floor");
                    if (generated == 0) {
                        var p = new net.minecraft.core.BlockPos(x, top, z);
                        for (var status : List.of(net.minecraft.world.level.chunk.status.ChunkStatus.NOISE,
                                net.minecraft.world.level.chunk.status.ChunkStatus.SURFACE)) {
                            var chunk = level.getChunk(x >> 4, z >> 4, status);
                            if (!chunk.getBlockState(p).isAir()) throw new AssertionError("Native fill or skin closed skylight: " + p);
                        }
                        generated++;
                    }
                }
                var row = new JsonObject(); row.addProperty("kind", kind.name()); row.add("island", new Gson().toJsonTree(island));
                row.add("style", new Gson().toJsonTree(styles.style(island)));
                row.addProperty("centerFragmentClearance", FragmentedExterior.sample(island.seed(), 0, 0,
                        styles.profile().fractures()).conservativeClearance());
                row.addProperty("openColumns", open); row.addProperty("nativeRoofWitnesses", generated); rows.add(row);
                if (kind == IslandInteriors.Kind.HOLLOW && profile.skylights().enabled() && open == 0)
                    throw new AssertionError("Selected HOLLOW fixture has no skylight: " + row
                            + "; center exterior=" + planning.skyweave$planningColumn(island.centerX(), island.centerZ(), settings, TerrainStage.EXTERIOR)
                            + "; center interior=" + planning.skyweave$planningColumn(island.centerX(), island.centerZ(), settings, TerrainStage.INTERIOR));
            }
        }
        // Fractured cells with insufficient clearance deliberately suppress interiors.
        // Keep a negative witness rather than demanding a skylight through protected rock.
        for (var island : openings) {
            if (island.archetype().waterBased() || styles.style(island).kind() == IslandStyles.Kind.NATIVE
                    || IslandInteriors.kind(island, profile) != IslandInteriors.Kind.HOLLOW
                    || FragmentedExterior.sample(island.seed(), 0, 0, styles.profile().fractures())
                            .conservativeClearance() >= profile.shellThickness()) continue;
            var exterior = planning.skyweave$planningColumn(island.centerX(), island.centerZ(), settings, TerrainStage.EXTERIOR);
            var interior = planning.skyweave$planningColumn(island.centerX(), island.centerZ(), settings, TerrainStage.INTERIOR);
            if (exterior.dominantDescriptor() == null || exterior.dominantDescriptor().seed() != island.seed()
                    || exterior.segments().isEmpty()) continue;
            if (!exterior.segments().equals(interior.segments()))
                throw new AssertionError("Protected fractured center was excavated: " + island);
            var row = new JsonObject(); row.addProperty("kind", "PROTECTED_FRACTURE");
            row.add("island", new Gson().toJsonTree(island)); row.add("style", new Gson().toJsonTree(styles.style(island)));
            row.addProperty("openColumns", 0); row.addProperty("nativeRoofWitnesses", 0); rows.add(row);
            break;
        }
        return rows;
    }
}
