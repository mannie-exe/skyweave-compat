package design.inherent.skyweavecompat.probe;

import com.google.gson.*;
import design.inherent.skyweavecompat.NeighborConfig;
import design.inherent.skyweavecompat.placement.*;
import design.inherent.skyweavecompat.terrain.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;
import org.sathrek.sky_archipelago.worldgen.generator.field.model.IslandFamily;

/** Bounded native starts and real host blocks for the narrow footprint-gap policy. */
final class SkyVillageProbe {
    static void run(MinecraftServer server, Path output) throws Exception {
        var level = server.overworld();
        var generator = (SkyIslandChunkGenerator)level.getChunkSource().getGenerator();
        var settings = generator.resolvedSkyIslandSettings();
        var field = ((GeologicalGenerator)(Object)generator).skyweave$field(level.getChunkSource().randomState());
        var structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE)
                .getHolderOrThrow(net.minecraft.resources.ResourceKey.create(Registries.STRUCTURE, ResourceLocation.parse("skyvillages:skyvillage")));
        var placement = (ArchipelagoVillagePlacement)level.getChunkSource().getGeneratorState().getPlacementsForStructure(structure).getFirst();
        var report = new JsonObject(); report.addProperty("complete", false);
        report.add("policy", new Gson().toJsonTree(NeighborConfig.settings()));
        var rows = new JsonArray(); report.add("candidates", rows);
        int accepted = 0;
        for (var preview : field.collectIslandPreviewsInRadius(0, 0, 16000, settings).stream()
                .filter(p -> p.family() == IslandFamily.ANCHOR_PLATEAU)
                .sorted(Comparator.comparingDouble(p -> Math.hypot(p.x(), p.z()))).limit(32).toList()) {
            var anchor = placement.owner(preview.x(), preview.z());
            if (anchor == null || VillageDistribution.forAnchor(anchor.seed()) == 0) continue;
            var sites = placement.sites(anchor);
            if (sites.size() > VillageDistribution.forAnchor(anchor.seed())) throw new AssertionError("Allowance exceeded");
            for (var site : sites) {
                if (!anchor.equals(placement.owner(site)) || !placement.isStructureChunk(level.getChunkSource().getGeneratorState(), site.chunkX(), site.chunkZ()))
                    throw new AssertionError("Candidate ownership/grid mismatch");
                var row = new JsonObject(); row.add("site", new Gson().toJsonTree(site)); row.add("anchor", new Gson().toJsonTree(anchor)); rows.add(row);
                var chunk = level.getChunk(site.chunkX(), site.chunkZ(), ChunkStatus.STRUCTURE_STARTS);
                var start = chunk.getStartForStructure(structure.value());
                boolean valid = start != null && start.isValid(); row.addProperty("valid", valid);
                if (valid) {
                    var box = start.getBoundingBox();
                    var footprint = new NeighborRule.Footprint(box.minX(), box.minZ(), box.maxX(), box.maxZ());
                    var terrain = new IslandTerrain(field, settings, anchor);
                    var decision = NeighborRule.evaluate(footprint, NeighborConfig.settings(), terrain);
                    row.addProperty("decision", decision.name()); row.addProperty("pieces", start.getPieces().size());
                    row.add("bounds", new Gson().toJsonTree(List.of(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())));
                    if (decision != NeighborRule.Decision.ACCEPTED) throw new AssertionError("Native start violates final footprint policy");
                    boolean host = false;
                    int gap = NeighborConfig.settings().maximumGap();
                    find: for (int x = box.minX()-gap; x <= box.maxX()+gap; x += 16)
                        for (int z = box.minZ()-gap; z <= box.maxZ()+gap; z += 16) {
                            if (footprint.distanceSquared(x,z) > (long)gap*gap || terrain.islandRadius(x,z) < 96) continue;
                            var exterior = ((InteriorField)(Object)field).skyweave$planningColumn(x,z,settings,TerrainStage.EXTERIOR).profile();
                            var actual = level.getChunk(x>>4,z>>4,ChunkStatus.NOISE);
                            for (int y = exterior.topY(); y >= exterior.bottomY(); y--) if (!actual.getBlockState(new BlockPos(x,y,z)).isAir()) {
                                row.addProperty("generatedHost", new BlockPos(x,y,z).toShortString());
                                row.addProperty("hostGap", Math.sqrt(footprint.distanceSquared(x,z))); host = true; break find;
                            }
                        }
                    if (!host) throw new AssertionError("No generated host body near accepted start");
                    var piece = start.getPieces().getFirst().getBoundingBox();
                    int cx = Math.floorDiv((piece.minX() + piece.maxX()) / 2, 16);
                    int cz = Math.floorDiv((piece.minZ() + piece.maxZ()) / 2, 16);
                    var building = level.getChunk(cx, cz, ChunkStatus.FULL);
                    var blocks = new TreeMap<String, Integer>();
                    for (int x = Math.max(piece.minX(), cx * 16); x <= Math.min(piece.maxX(), cx * 16 + 15); x++)
                        for (int z = Math.max(piece.minZ(), cz * 16); z <= Math.min(piece.maxZ(), cz * 16 + 15); z++)
                            for (int y = piece.minY(); y <= piece.maxY(); y++) {
                                var state = building.getBlockState(new BlockPos(x,y,z));
                                if (!state.isAir()) blocks.merge(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(),1,Integer::sum);
                            }
                    if (blocks.isEmpty()) throw new AssertionError("Accepted Sky Village has no placed blocks in its FULL building chunk");
                    row.add("placedBuildingBlocks", new Gson().toJsonTree(blocks));
                    row.addProperty("buildingChunk", cx + "," + cz);
                    accepted++;
                }
                Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report));
                if (accepted >= 2 || rows.size() >= 8) break;
            }
            if (accepted >= 2 || rows.size() >= 8) break;
        }
        report.addProperty("accepted", accepted); report.addProperty("complete", accepted >= 2);
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report));
        if (accepted < 2) throw new AssertionError("Insufficient native close-range Sky Village controls");
    }
    private SkyVillageProbe() {}
}
