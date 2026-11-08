package design.inherent.skyweavecompat.probe;

import com.google.gson.*;
import design.inherent.skyweavecompat.terrain.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;
import org.sathrek.sky_archipelago.worldgen.generator.field.model.IslandDescriptor;
import java.nio.file.*;
import java.util.*;

/** Bounded natural decoration evidence for actual island surfaces, not synthetic feature frequencies. */
final class EcologyProbe {
    private record Site(String kind, int x, int z) {}

    static void run(MinecraftServer server, Path output) throws Exception {
        var report = new JsonObject();
        try {
            var level = server.overworld();
            var generator = (SkyIslandChunkGenerator)level.getChunkSource().getGenerator();
            var settings = generator.resolvedSkyIslandSettings();
            var field = ((GeologicalGenerator)(Object)generator).skyweave$field(level.getChunkSource().randomState());
            var planning = (InteriorField)(Object)field;
            var hosts = new ArrayList<IslandDescriptor>();
            int before = level.getChunkSource().getLoadedChunksCount();
            for (int cx = -4; cx <= 4; cx++) for (int cz = -4; cz <= 4; cz++) {
                var members = ((ClusterField)(Object)field).skyweave$members(cx, cz, settings);
                if (!members.isEmpty() && !members.getFirst().archetype().waterBased()) hosts.add(members.getFirst());
            }
            hosts.sort(Comparator.comparingDouble(host -> Math.hypot(host.centerX(), host.centerZ())));
            var sites = new LinkedHashMap<String, Site>();
            for (var host : hosts) {
                String kind = IslandInteriors.kind(host, InteriorConfiguration.profile()).name();
                var column = field.sampleColumn(host.centerX(), host.centerZ(), settings);
                if (column.exists()) sites.putIfAbsent(kind, new Site(kind, host.centerX(), host.centerZ()));
                if (!sites.containsKey("APRON")) {
                    int reach = host.maxRadius() * 2;
                    search: for (int x = host.centerX() - reach; x <= host.centerX() + reach; x += 16)
                        for (int z = host.centerZ() - reach; z <= host.centerZ() + reach; z += 16)
                            if (planning.skyweave$isApron(host, x, z, settings) && field.sampleColumn(x, z, settings).exists()) {
                                sites.put("APRON", new Site("APRON", x, z)); break search;
                            }
                }
                if (sites.size() == IslandInteriors.Kind.values().length + 1) break;
            }
            if (before != level.getChunkSource().getLoadedChunksCount()) throw new AssertionError("Ecology site selection loaded chunks");
            if (!sites.containsKey("APRON") || Arrays.stream(IslandInteriors.Kind.values()).anyMatch(kind -> !sites.containsKey(kind.name())))
                throw new AssertionError("Missing bounded ecology host/fragment controls: " + sites.keySet());
            report.addProperty("seed", Long.toString(level.getSeed()));
            report.addProperty("evidence", "Natural native chunk stages; block counts are bounded witnesses, not feature frequency estimates");
            var rows = new JsonArray(); report.add("sites", rows);
            var pos = new BlockPos.MutableBlockPos();
            for (var site : sites.values()) {
                var row = new JsonObject(); rows.add(row);
                row.addProperty("kind", site.kind()); row.addProperty("x", site.x()); row.addProperty("z", site.z());
                var column = field.sampleColumn(site.x(), site.z(), settings);
                row.addProperty("plannedTop", column.topY()); row.addProperty("plannedBottom", column.bottomY());
                var interior = planning.skyweave$planningColumn(site.x(), site.z(), settings, TerrainStage.INTERIOR).segments();
                row.add("interiorSpans", new Gson().toJsonTree(interior));
                int cx = Math.floorDiv(site.x(), 16), cz = Math.floorDiv(site.z(), 16);
                var stages = new JsonObject(); row.add("stages", stages);
                for (var status : List.of(ChunkStatus.NOISE, ChunkStatus.SURFACE, ChunkStatus.CARVERS, ChunkStatus.FEATURES, ChunkStatus.FULL)) {
                    var chunk = level.getChunk(cx, cz, status);
                    var observation = new JsonObject(); stages.add(status.toString(), observation);
                    observation.addProperty("actualStatus", chunk.getPersistedStatus().toString());
                    observation.addProperty("plannedSurface", chunk.getBlockState(pos.set(site.x(), column.topY(), site.z())).toString());
                }
                // Complete a bounded neighborhood before testing survival across the center chunk's edge.
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) level.getChunk(cx + dx, cz + dz);
                var chunk = level.getChunk(cx, cz);
                var biome = chunk.getNoiseBiome(site.x() >> 2, column.topY() >> 2, site.z() >> 2);
                row.addProperty("biome", biome.getRegisteredName());
                var featureIds = new TreeSet<String>();
                biome.value().getGenerationSettings().features().forEach(step -> step.forEach(feature -> feature.unwrapKey()
                        .filter(key -> key.location().getNamespace().equals("geophilic"))
                        .ifPresent(key -> featureIds.add(key.location().toString()))));
                row.add("resolvedGeophilicFeatures", new Gson().toJsonTree(featureIds));
                if (featureIds.isEmpty()) throw new AssertionError("No resolved Geophilic decoration in fixture biome " + biome.getRegisteredName());
                var counts = new TreeMap<String, Integer>();
                int plants = 0, unsupportedPlants = 0;
                for (int x = cx * 16; x < cx * 16 + 16; x++) for (int z = cz * 16; z < cz * 16 + 16; z++)
                    for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y++) {
                        var state = chunk.getBlockState(pos.set(x, y, z));
                        if (state.isAir()) continue;
                        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                        counts.merge(id, 1, Integer::sum);
                        if (state.getBlock() instanceof net.minecraft.world.level.block.BushBlock) {
                            plants++;
                            if (!state.canSurvive(level, pos)) unsupportedPlants++;
                        }
                    }
                row.add("blocks", new Gson().toJsonTree(counts));
                row.addProperty("plants", plants); row.addProperty("unsupportedPlants", unsupportedPlants);
                if (unsupportedPlants > 0) throw new AssertionError("Unsupported plants at " + site + ": " + unsupportedPlants);
                // These deliberate calls test rare feature mechanics separately from natural frequency.
                var direct = new JsonArray(); row.add("directConfiguredControls", direct);
                var configured = level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE);
                var controls = biome.is(ResourceLocation.withDefaultNamespace("desert"))
                        ? List.of("lava_lake/sandstone") : biome.is(ResourceLocation.withDefaultNamespace("stony_shore"))
                        ? List.of("biome_specific/stony_shore/puddle", "biome_specific/stony_shore/pillar/small", "biome_specific/stony_shore/pillar/large")
                        : List.<String>of();
                for (String name : controls) {
                    var feature = configured.get(ResourceLocation.fromNamespaceAndPath("geophilic", name));
                    if (feature == null) throw new AssertionError("Missing configured control " + name);
                    int x = cx * 16 + 8, z = cz * 16 + 8;
                    int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG, x, z);
                    var origin = new BlockPos(x, y, z);
                    var baseline = new ArrayList<net.minecraft.world.level.block.state.BlockState>();
                    for (int dx = -12; dx <= 12; dx++) for (int dz = -12; dz <= 12; dz++) for (int dy = -16; dy <= 24; dy++)
                        baseline.add(level.getBlockState(origin.offset(dx, dy, dz)));
                    boolean success = feature.place(level, generator, net.minecraft.util.RandomSource.create(level.getSeed()), origin);
                    int index = 0, changed = 0, fluidChanges = 0;
                    for (int dx = -12; dx <= 12; dx++) for (int dz = -12; dz <= 12; dz++) for (int dy = -16; dy <= 24; dy++) {
                        var state = level.getBlockState(origin.offset(dx, dy, dz));
                        if (state != baseline.get(index++)) { changed++; if (!state.getFluidState().isEmpty()) fluidChanges++; }
                    }
                    var control = new JsonObject(); direct.add(control);
                    control.addProperty("id", "geophilic:" + name); control.addProperty("origin", origin.toShortString());
                    control.addProperty("nativeSuccess", success); control.addProperty("changedBlocks", changed);
                    control.addProperty("changedFluidBlocks", fluidChanges);
                }
            }
            report.addProperty("complete", true);
        } catch (Throwable failure) {
            report.addProperty("complete", false); report.addProperty("failure", failure.toString()); throw failure;
        } finally {
            Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
        }
    }
    private EcologyProbe() {}
}
