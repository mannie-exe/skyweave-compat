package design.inherent.skyweavecompat.probe;

import com.google.gson.*;
import design.inherent.skyweavecompat.terrain.GeologicalGenerator;
import design.inherent.skyweavecompat.terrain.structure.ArchipelagoStructurePlacement;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;
import java.nio.file.*;

/** Native presence checks for fresh, provably unselected void candidates; never loads their chunks. */
final class LookupProbe {
    static void run(MinecraftServer server, Path output) throws Exception {
        var level = server.overworld();
        var generator = (SkyIslandChunkGenerator)level.getChunkSource().getGenerator();
        var state = level.getChunkSource().getGeneratorState();
        var field = ((GeologicalGenerator)(Object)generator).skyweave$field(level.getChunkSource().randomState());
        var settings = generator.resolvedSkyIslandSettings();
        var targetId = ResourceLocation.parse(System.getProperty("neighbors.probe.lookupStructure", "minecraft:ancient_city"));
        var target = server.registryAccess().registryOrThrow(Registries.STRUCTURE)
                .getHolder(targetId).orElseThrow();
        var placement = state.getPlacementsForStructure(target).stream()
                .filter(ArchipelagoStructurePlacement.class::isInstance)
                .map(ArchipelagoStructurePlacement.class::cast).findFirst().orElseThrow();
        var report = new JsonObject(); var rows = new JsonArray(); int rejected = 0;
        report.addProperty("structure", targetId.toString());
        long began = System.nanoTime();
        for (int i = 0; i < 64 && rows.size() < 8; i++) {
            var chunk = placement.getPotentialStructureChunk(level.getSeed(), 6000 + i * 8, -6000);
            if (field.sampleDominantIslandDescriptor(chunk.getMiddleBlockX(), chunk.getMiddleBlockZ(), settings) != null) continue;
            if (placement.isStructureChunk(state, chunk.x, chunk.z)) throw new AssertionError("Void selected for structure");
            var result = level.structureManager().checkStructurePresence(chunk, target.value(), placement, false);
            var row = new JsonObject(); row.addProperty("chunkX", chunk.x); row.addProperty("chunkZ", chunk.z);
            row.addProperty("presence", result.name());
            row.addProperty("loaded", level.getChunkSource().hasChunk(chunk.x, chunk.z));
            if (level.getChunkSource().hasChunk(chunk.x, chunk.z)) throw new AssertionError("Presence check loaded a chunk");
            if (result == StructureCheckResult.START_NOT_PRESENT) rejected++;
            rows.add(row);
        }
        report.add("candidates", rows); report.addProperty("rejectedWithoutLoad", rejected);
        boolean positive = true;
        // Retained native city witness from the pre-variation seed-42 fixture. Keep lookup
        // verification separate from FULL realization and its block/loot assertions.
        if (Boolean.getBoolean("neighbors.probe.cityWitness")) {
            int x = 707, z = 361;
            positive = placement.isStructureChunk(state, x, z);
            var witness = new JsonObject(); witness.addProperty("selected", positive);
            if (positive) {
                var found = generator.findNearestMapStructure(level, HolderSet.direct(target),
                        new BlockPos(x * 16 + 8, 200, z * 16 + 8), 1, false);
                positive = found != null;
                if (found != null) {
                    witness.addProperty("located", found.getFirst().toShortString());
                    var chunk = level.getChunk(x, z, net.minecraft.world.level.chunk.status.ChunkStatus.STRUCTURE_STARTS);
                    var start = chunk.getStartForStructure(target.value());
                    positive = start != null && start.isValid();
                    if (positive) {
                        witness.addProperty("bounds", start.getBoundingBox().toString());
                        witness.addProperty("pieces", start.getPieces().size());
                        witness.addProperty("presenceAfterPartialStart", level.structureManager()
                                .checkStructurePresence(chunk.getPos(), target.value(), placement, false).name());
                        // Protochunk starts need not have been published to StructureCheck's loaded
                        // cache yet. Verify native repeated discovery, not an assumed cache state.
                        var again = generator.findNearestMapStructure(level, HolderSet.direct(target),
                                new BlockPos(x * 16 + 8, 200, z * 16 + 8), 1, false);
                        positive = again != null && again.getFirst().equals(found.getFirst());
                    }
                }
            }
            report.add("cityWitness", witness);
        }
        int radius = Integer.getInteger("neighbors.probe.lookupRadius", 0);
        if (radius > 0) {
            var observation = new JsonObject();
            long coldStart = System.nanoTime();
            var found = generator.findNearestMapStructure(level, HolderSet.direct(target), level.getSharedSpawnPos(), radius, false);
            double coldSeconds = (System.nanoTime() - coldStart) / 1e9;
            long warmStart = System.nanoTime();
            var again = generator.findNearestMapStructure(level, HolderSet.direct(target), level.getSharedSpawnPos(), radius, false);
            double warmSeconds = (System.nanoTime() - warmStart) / 1e9;
            boolean same = found == null ? again == null : again != null && found.getFirst().equals(again.getFirst());
            observation.addProperty("radiusRings", radius); observation.addProperty("coldSeconds", coldSeconds);
            observation.addProperty("warmSeconds", warmSeconds); observation.addProperty("sameResult", same);
            if (found != null) observation.addProperty("located", found.getFirst().toShortString());
            report.add("nativeLookupFromSpawn", observation);
            positive &= same && coldSeconds < 55 && warmSeconds < 55;
        }
        report.addProperty("elapsedSeconds", (System.nanoTime() - began) / 1e9);
        report.addProperty("complete", rows.size() == 8 && rejected == rows.size() && positive);
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
    }
    private LookupProbe() {}
}
