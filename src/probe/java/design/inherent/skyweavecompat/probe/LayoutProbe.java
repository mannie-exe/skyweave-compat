package design.inherent.skyweavecompat.probe;

import org.sathrek.sky_archipelago.worldgen.generator.field.model.BiomeTerrainSampler;
import org.sathrek.sky_archipelago.worldgen.generator.field.model.IslandDescriptor;
import com.google.gson.*;
import design.inherent.skyweavecompat.terrain.*;
import java.util.*;
import org.sathrek.sky_archipelago.config.SkyIslandSettings;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;

/** Fresh native caches, reordered queries and real companion membership across cell boundaries. */
final class LayoutProbe {
    private LayoutProbe() {}
    static JsonObject run(IslandField field, SkyIslandSettings settings) throws Exception {
        var p = ((ProfiledTerrain)(Object)field).skyweave$profile();
        var sampler = IslandField.class.getDeclaredField("biomeTerrainSampler"); sampler.setAccessible(true);
        var reverse = new IslandField(field.layoutSeed(), (BiomeTerrainSampler)sampler.get(field));
        var parallel = new IslandField(field.layoutSeed(), (BiomeTerrainSampler)sampler.get(field));
        ((ProfiledTerrain)(Object)reverse).skyweave$configure(p); ((ProfiledTerrain)(Object)parallel).skyweave$configure(p);
        var cells = new ArrayList<ClusterLayout.Cell>();
        for (int x = -7; x <= 7; x++) for (int z = -7; z <= 7; z++) cells.add(new ClusterLayout.Cell(x, z));
        var expected = new LinkedHashMap<ClusterLayout.Cell, List<IslandDescriptor>>();
        var membership = (ClusterField)(Object)field;
        for (var cell : cells) expected.put(cell, membership.skyweave$members(cell.x(), cell.z(), settings));
        Collections.reverse(cells);
        for (var cell : cells) if (!expected.get(cell).equals(((ClusterField)(Object)reverse).skyweave$members(cell.x(), cell.z(), settings))) {
            throw new AssertionError("Accepted layout depends on query order");
        }
        cells.parallelStream().forEach(cell -> {
            if (!expected.get(cell).equals(((ClusterField)(Object)parallel).skyweave$members(cell.x(), cell.z(), settings))) {
                throw new AssertionError("Accepted layout depends on concurrent queries");
            }
        });
        var candidate = IslandField.class.getDeclaredMethod("skyweave$candidate", int.class, int.class, SkyIslandSettings.class);
        candidate.setAccessible(true);
        int eligible = 0, accepted = 0, crossing = 0, supportedCrossing = 0;
        for (var cell : cells) {
            if (candidate.invoke(field, cell.x(), cell.z(), settings) != null) eligible++;
            var members = expected.get(cell);
            if (members.isEmpty()) continue;
            accepted++;
            var anchor = members.getFirst();
            var frame = IslandGeometry.geologicalFrame(anchor);
            for (var island : members) {
                if (Math.floorDiv(island.centerX(), p.spacing()) == cell.x() && Math.floorDiv(island.centerZ(), p.spacing()) == cell.z()) continue;
                crossing++;
                if (!membership.skyweave$owner(island, settings).equals(cell)) throw new AssertionError("Displaced companion changed owners");
                var dominant = field.sampleDominantIslandDescriptor(island.centerX(), island.centerZ(), settings);
                if (dominant == null || !members.contains(dominant)) continue;
                var actual = ((GeologicalField)(Object)field).skyweave$originFrameAt(island.centerX(), island.centerZ(), settings);
                if (!frame.equals(actual)) throw new AssertionError("Companion geological frame follows the wrong cell");
                supportedCrossing++;
            }
        }
        if (crossing == 0 || supportedCrossing == 0) throw new AssertionError("Missing cross-cell companion fixture");
        var result = new JsonObject(); result.addProperty("cells", cells.size());
        result.addProperty("eligibleCandidates", eligible); result.addProperty("acceptedClusters", accepted);
        result.addProperty("rejectedByClearance", eligible - accepted); result.addProperty("crossCellCompanions", crossing);
        result.addProperty("crossCellGeologicalFrames", supportedCrossing); result.addProperty("reverseOrderMatches", true);
        result.addProperty("concurrentQueriesMatch", true);
        return result;
    }
}
