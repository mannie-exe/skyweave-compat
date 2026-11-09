package design.inherent.skyweavecompat.probe;

import com.google.gson.*;
import design.inherent.skyweavecompat.terrain.GeologicalGenerator;
import design.inherent.skyweavecompat.terrain.structure.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;
import org.sathrek.sky_archipelago.worldgen.generator.field.model.IslandFamily;

/** Bounded native allowance census and fit diagnostics for dependency/tuning reviews. */
final class SettlementReviewProbe {
    private SettlementReviewProbe() {}

    static void run(MinecraftServer server, Path output, boolean fit) throws Exception {
        var level = server.overworld();
        var generator = (SkyIslandChunkGenerator)level.getChunkSource().getGenerator();
        var field = ((GeologicalGenerator)(Object)generator).skyweave$field(level.getChunkSource().randomState());
        var settings = generator.resolvedSkyIslandSettings();
        var distribution = ((StructurePlacementState)level.getChunkSource().getGeneratorState()).skyweave$structureDistribution();
        var report = new JsonObject(); report.addProperty("seed", level.getSeed());
        report.addProperty("complete", false);
        report.addProperty("scope", fit ? "Allowances and two bounded fit controls; no structure creation or encounter-rate estimate" : "Allowances before fitting; no structure creation or encounter-rate estimate");
        var rows = new JsonArray(); report.add("clusters", rows);
        var owners = new HashSet<StructureSelection.Owner>();
        // Probe-only reflection observes the shipping selector without exposing another runtime API.
        var choice = StructureDistribution.class.getDeclaredMethod("choice", StructureSelection.Owner.class, StructureFamily.class);
        choice.setAccessible(true);
        for (var anchor : field.collectIslandPreviewsInRadius(0, 0, 16000, settings).stream()
                .filter(p -> p.family() == IslandFamily.ANCHOR_PLATEAU)
                .sorted(Comparator.comparingDouble(p -> Math.hypot(p.x(), p.z())))
                .limit(Math.clamp(Integer.getInteger("neighbors.probe.structureClusters", 16), 1, 64)).toList()) {
            var owner = distribution.owner(anchor.terrainPlan().descriptor());
            if (!owners.add(owner)) continue;
            var row = new JsonObject(); row.add("owner", new Gson().toJsonTree(owner));
            row.add("center", new Gson().toJsonTree(List.of(anchor.x(), anchor.y(), anchor.z())));
            for (var family : List.of(StructureFamily.VILLAGES, StructureFamily.MANSIONS)) {
                var selected = choice.invoke(distribution, owner, family);
                var area = selected.getClass().getDeclaredMethod("area"); area.setAccessible(true);
                var count = selected.getClass().getDeclaredMethod("count"); count.setAccessible(true);
                var familyRow = new JsonObject();
                familyRow.addProperty("eligibleChunks", (Double)area.invoke(selected));
                familyRow.addProperty("allowance", (Integer)count.invoke(selected));
                row.add(family.key, familyRow);
            }
            rows.add(row);
        }
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report));

        if (fit) fitVillages(distribution, rows, report, output);
        report.addProperty("complete", true);
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }

    private static void fitVillages(StructureDistribution distribution, JsonArray rows, JsonObject report, Path output) throws Exception {
        var environmentField = StructureDistribution.class.getDeclaredField("environment"); environmentField.setAccessible(true);
        var environment = environmentField.get(distribution);
        if (environment == null) throw new AssertionError("Native fitting context is not bound");
        var entriesField = StructureDistribution.class.getDeclaredField("familyEntries"); entriesField.setAccessible(true);
        var entries = (Map<?, ?>)entriesField.get(distribution);
        var choose = StructureDistribution.class.getDeclaredMethod("choice", StructureSelection.Owner.class, StructureFamily.class);
        choose.setAccessible(true);
        var type = Class.forName("design.inherent.skyweavecompat.terrain.structure.VillageLayoutPlanning");
        var fit = type.getDeclaredMethod("fit", StructureDistribution.class,
                org.sathrek.sky_archipelago.worldgen.generator.structure.StructurePlacementPassContext.class,
                StructureSelection.Site.class, List.class); fit.setAccessible(true);
        var fits = new JsonArray(); report.add("villageFitSamples", fits);
        for (var element : rows) {
            var row = element.getAsJsonObject();
            if (row.getAsJsonObject("villages").get("allowance").getAsInt() == 0) continue;
            if (fits.size() == 2) break;
            var owner = new Gson().fromJson(row.get("owner"), StructureSelection.Owner.class);
            var choice = choose.invoke(distribution, owner, StructureFamily.VILLAGES);
            var ranked = choice.getClass().getDeclaredMethod("ranked"); ranked.setAccessible(true);
            var sites = (List<?>)ranked.invoke(choice);
            var observations = new JsonArray(); var result = new JsonObject(); result.add("owner", row.get("owner"));
            result.add("attempts", observations); fits.add(result);
            for (int i = 0; i < Math.min(8, sites.size()); i++) {
                long began = System.nanoTime();
                var layout = (StructureLayout)fit.invoke(null, distribution, environment, sites.get(i), entries.get(StructureFamily.VILLAGES));
                var observation = new JsonObject(); observation.add("site", new Gson().toJsonTree(sites.get(i)));
                observation.addProperty("milliseconds", (System.nanoTime()-began)/1_000_000);
                observation.addProperty("accepted", layout != null);
                if (layout != null) { observation.addProperty("id", layout.id().toString()); observation.addProperty("pieces", layout.pieces().size()); }
                observations.add(observation);
                Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report));
                if (layout != null) break;
            }
        }
        if (fits.size() != 2) throw new AssertionError("Insufficient positive allowance controls");
    }

}
