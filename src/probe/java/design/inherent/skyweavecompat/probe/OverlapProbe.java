package design.inherent.skyweavecompat.probe;

import org.sathrek.sky_archipelago.worldgen.generator.field.model.IslandFamily;
import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import design.inherent.skyweavecompat.terrain.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.RandomState;
import org.sathrek.sky_archipelago.config.SkyIslandGeneratorSettings;
import org.sathrek.sky_archipelago.config.SkyIslandSettings;
import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;
import java.nio.file.*;
import java.util.Comparator;

/** Matched native exterior columns; this does not claim feature or fluid verification. */
final class OverlapProbe {
    private OverlapProbe() {}

    static SkyIslandSettings mode(SkyIslandSettings settings, String mode) {
        var json = SkyIslandGeneratorSettings.CODEC.encodeStart(JsonOps.INSTANCE,
                SkyIslandGeneratorSettings.fromRuntimeSettings(settings)).getOrThrow().getAsJsonObject();
        json.getAsJsonObject("advanced").addProperty("terrain_overlap_mode", mode);
        return SkyIslandGeneratorSettings.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow().toRuntimeSettings();
    }

    static void run(MinecraftServer server, Path output) throws Exception {
        long began = System.nanoTime();
        var report = new JsonObject();
        report.addProperty("evidence", "matched native exterior columns before carvers, features and new interiors");
        report.add("profile", new Gson().toJsonTree(GenerationConfiguration.profile()));
        var rows = new JsonArray();
        int loaded=server.overworld().getChunkSource().getLoadedChunksCount();
        for (long seed : new long[]{1, 42, 8675309}) {
            var preset = server.registryAccess().registryOrThrow(Registries.WORLD_PRESET)
                    .get(ResourceLocation.parse(GenerationProfile.ID));
            var generator = (SkyIslandChunkGenerator)preset.createWorldDimensions().overworld();
            var random = RandomState.create(generator.generatorSettings().value(),
                    server.registryAccess().registryOrThrow(Registries.NOISE).asLookup(), seed);
            var field = ((GeologicalGenerator)(Object)generator).skyweave$field(random);
            var settings = generator.resolvedSkyIslandSettings();
            var voidSettings = mode(settings, "void");
            var overlapSettings = mode(settings, "overlap");
            var anchors = field.collectIslandPreviewsInRadius(0, 0, 12000, settings).stream()
                    .filter(p -> p.family() == IslandFamily.ANCHOR_PLATEAU)
                    .sorted(Comparator.comparingDouble(p -> Math.hypot(p.x(), p.z()))).limit(2).toList();
            for (var anchor : anchors) {
                long columns = 0, occupied = 0, stacked = 0, voidVolume = 0, overlapVolume = 0;
                int maximumSegments = 0, maximumAdded = 0, pointColumns = 0;
                long pointChecks=0, simultaneousCavitySolid=0, distinctDatumPoints=0, differentLayerColumns=0,biomeOwnerChecks=0;
                long thermalChecks=0,rawThermalDisagreements=0,climateCaptureNanos=0;int maximumClimateLayers=0;
                var examples = new JsonArray();
                int reach = GenerationConfiguration.profile().clusterReach();
                for (int x = anchor.x() - reach; x <= anchor.x() + reach; x += 24)
                    for (int z = anchor.z() - reach; z <= anchor.z() + reach; z += 24) {
                        columns++;
                        var before = ((InteriorField)(Object)field).skyweave$exteriorAt(x, z, voidSettings);
                        var after = ((InteriorField)(Object)field).skyweave$exteriorAt(x, z, overlapSettings);
                        if (before.isEmpty() != after.isEmpty()
                                || !before.isEmpty() && !before.getFirst().equals(after.getFirst())) {
                            throw new AssertionError("Overlap changed the upper exterior at " + x + "," + z);
                        }
                        if (after.isEmpty()) continue;
                        occupied++;
                        if(pointColumns<32 && Math.abs(x-anchor.x())<=96 && Math.abs(z-anchor.z())<=96) {
                            var context=((InteriorField)(Object)field).skyweave$contextAt(x,z,overlapSettings);
                            if(context.bodies().size()>1) {
                                var intervals=((InteriorField)(Object)field).skyweave$planningColumn(x,z,overlapSettings,TerrainStage.INTERIOR).segments();
                                long captureBegan=System.nanoTime();var climate=context.climate();climateCaptureNanos+=System.nanoTime()-captureBegan;
                                maximumClimateLayers=Math.max(maximumClimateLayers,climate.layers().size());
                                var rawClimate=new design.inherent.skyweavecompat.climate.ClimateColumn(context.bodies().stream().map(body->
                                        new design.inherent.skyweavecompat.climate.ClimateColumn.Layer(body.exterior().bottomY(),body.exterior().topY(),body.climateDatum())).toList());
                                var columnsData=new java.util.ArrayList<>(java.util.Collections.nCopies(256,design.inherent.skyweavecompat.climate.ClimateColumn.EMPTY));
                                columnsData.set((x&15)*16+(z&15),climate);
                                var packed=new design.inherent.skyweavecompat.climate.PackedClimateChunk(
                                        new design.inherent.skyweavecompat.climate.ClimateChunk(x>>4,z>>4,columnsData));
                                for(int y=GenerationProfile.MIN_Y;y<GenerationProfile.MAX_Y;y++) {
                                    final int py=y;
                                    boolean expected=intervals.stream().anyMatch(span->span.contains(py));
                                    if((context.solidOwner(y)!=null)!=expected)throw new AssertionError("Overlapping point/interval mismatch at "+x+","+y+","+z);
                                    var contributions=context.contributions(y);
                                    boolean solid=contributions.stream().anyMatch(c->c.occupancy()==IslandContext.Occupancy.SOLID);
                                    boolean cavity=contributions.stream().anyMatch(c->c.occupancy()==IslandContext.Occupancy.CAVITY);
                                    var biomeOwner=context.biomeOwner(y);
                                    if(solid && biomeOwner!=context.solidOwner(y))throw new AssertionError("Cavity/water stole material biome ownership");
                                    if(contributions.isEmpty() && biomeOwner!=null)throw new AssertionError("Outside air acquired underground ownership");
                                    var thermal=climate.referenceY(y,design.inherent.skyweavecompat.climate.ClimateProfile.DEFAULT);
                                    if(!java.util.Objects.equals(thermal,packed.referenceY(x,y,z,design.inherent.skyweavecompat.climate.ClimateProfile.DEFAULT)))
                                        throw new AssertionError("Captured/server-packed climate mismatch at "+x+","+y+","+z);
                                    if(biomeOwner!=null) {
                                        int reference=design.inherent.skyweavecompat.climate.ClimateColumn.referenceY(y,biomeOwner.climateDatum(),design.inherent.skyweavecompat.climate.ClimateProfile.DEFAULT);
                                        if(!java.util.Objects.equals(reference,thermal))throw new AssertionError("Thermal/occupancy owner mismatch at "+x+","+y+","+z);
                                        if(!java.util.Objects.equals(reference,rawClimate.referenceY(y,design.inherent.skyweavecompat.climate.ClimateProfile.DEFAULT)))rawThermalDisagreements++;
                                        thermalChecks++;
                                    }
                                    biomeOwnerChecks++;
                                    if(solid && cavity)simultaneousCavitySolid++;
                                    if(contributions.stream().map(c->c.body().climateDatum()).distinct().count()>1)distinctDatumPoints++;
                                    pointChecks++;
                                }
                                if(context.bodies().stream().map(IslandContext.Body::climateDatum).distinct().count()>1)differentLayerColumns++;
                                pointColumns++;
                            }
                        }
                        long oldThickness = before.stream().mapToInt(s -> s.thickness()).sum();
                        long newThickness = after.stream().mapToInt(s -> s.thickness()).sum();
                        voidVolume += oldThickness; overlapVolume += newThickness;
                        maximumSegments = Math.max(maximumSegments, after.size());
                        if (after.size() > 1) stacked++;
                        int added = (int)(newThickness - oldThickness);
                        if (added > maximumAdded) {
                            maximumAdded = added;
                            var example = new JsonObject();
                            example.addProperty("x", x); example.addProperty("z", z);
                            example.add("segments", new Gson().toJsonTree(after));
                            examples.add(example);
                        }
                    }
                var row = new JsonObject();
                row.addProperty("seed", Long.toString(seed)); row.add("anchor", new Gson().toJsonTree(anchor));
                row.addProperty("sampleStep", 24); row.addProperty("columns", columns);
                row.addProperty("occupiedColumns", occupied); row.addProperty("stackedColumns", stacked);
                row.addProperty("voidSampledBlocks", voidVolume); row.addProperty("overlapSampledBlocks", overlapVolume);
                row.addProperty("differentLayerColumns",differentLayerColumns);
                row.addProperty("pointColumns",pointColumns);row.addProperty("pointChecks",pointChecks);
                row.addProperty("biomeOwnerChecks",biomeOwnerChecks);
                row.addProperty("thermalOwnerChecks",thermalChecks);row.addProperty("rawExteriorThermalDisagreements",rawThermalDisagreements);
                row.addProperty("climateCaptureNanos",climateCaptureNanos);row.addProperty("maximumClimateLayers",maximumClimateLayers);
                row.addProperty("simultaneousCavitySolid",simultaneousCavitySolid);row.addProperty("distinctDatumPoints",distinctDatumPoints);
                row.addProperty("maximumSegments", maximumSegments); row.add("lowerBodyExamples", examples);
                rows.add(row);
            }
        }
        report.add("samples", rows); report.addProperty("upperIntervalsUnchanged", true);
        if(server.overworld().getChunkSource().getLoadedChunksCount()!=loaded)throw new AssertionError("Overlap climate metadata loaded chunks");
        report.addProperty("loadedChunksUnchanged",true);
        report.addProperty("elapsedSeconds", (System.nanoTime() - began) / 1e9);
        report.addProperty("complete", true);
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
    }
}
