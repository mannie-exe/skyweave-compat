package design.inherent.skyweavecompat.probe;

import com.google.gson.*;
import design.inherent.skyweavecompat.placement.ArchipelagoVillagePlacement;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import java.util.*;

/** Compare candidate pruning against the generation placement, including negative coordinates. */
final class SkyVillageSearchProbe {
    static JsonObject verify(ServerLevel level, int originX, int originZ) {
        var state = level.getChunkSource().getGeneratorState();
        var holder = level.registryAccess().registryOrThrow(Registries.STRUCTURE)
                .getHolder(ResourceKey.create(Registries.STRUCTURE, ResourceLocation.parse("skyvillages:skyvillage"))).orElseThrow();
        var placement = state.getPlacementsForStructure(holder).stream().filter(ArchipelagoVillagePlacement.class::isInstance)
                .map(ArchipelagoVillagePlacement.class::cast).findFirst().orElseThrow();
        var zero = placement.getLocatePos(new ChunkPos(0, 0));
        long padding = Math.max(Math.abs((long)zero.getX()), Math.abs((long)zero.getZ())) + 16;
        var domain = placement.searchOwners(originX, originZ, 6400, padding);
        net.minecraft.core.BlockPos witness = null;
        while (!domain.complete() && witness == null) {
            var cell = domain.next();
            for (var site : placement.searchSites(cell.x(), cell.z(), originX, originZ, 6400, padding)) {
                var point = placement.getLocatePos(new ChunkPos(site.chunkX(), site.chunkZ()));
                if (distance(point, originX, originZ) <= 6400L * 6400) { witness = point; break; }
            }
        }
        if (witness == null) throw new AssertionError("Known Sky Village fixture has no planned candidate");
        var cases = new JsonArray();
        for (int[] center : List.of(new int[]{witness.getX(), witness.getZ()},
                new int[]{-Math.abs(witness.getX()) - 512, -Math.abs(witness.getZ()) - 512})) {
            int x = center[0], z = center[1], radius = 768;
            var planned = new HashSet<ChunkPos>();
            var cells = placement.searchOwners(x, z, radius, padding);
            while (!cells.complete()) {
                var cell = cells.next();
                for (var site : placement.searchSites(cell.x(), cell.z(), x, z, radius, padding)) {
                    var pos = new ChunkPos(site.chunkX(), site.chunkZ());
                    if (distance(placement.getLocatePos(pos), x, z) <= (long)radius * radius) planned.add(pos);
                }
            }
            var expected = new HashSet<ChunkPos>(); int checked = 0;
            int minX = Math.toIntExact(Math.floorDiv(Math.floorDiv(x - radius - padding, 16), placement.spacing()) - 1);
            int maxX = Math.toIntExact(Math.floorDiv(Math.floorDiv(x + radius + padding, 16), placement.spacing()) + 1);
            int minZ = Math.toIntExact(Math.floorDiv(Math.floorDiv(z - radius - padding, 16), placement.spacing()) - 1);
            int maxZ = Math.toIntExact(Math.floorDiv(Math.floorDiv(z + radius + padding, 16), placement.spacing()) + 1);
            for (int gx = minX; gx <= maxX; gx++) for (int gz = minZ; gz <= maxZ; gz++) {
                var pos = placement.getPotentialStructureChunk(level.getSeed(), gx * placement.spacing(), gz * placement.spacing());
                if (distance(placement.getLocatePos(pos), x, z) > (long)radius * radius) continue;
                checked++;
                if (placement.isStructureChunk(state, pos.x, pos.z)) expected.add(pos);
            }
            var verified = new HashSet<>(planned);
            verified.removeIf(pos -> !placement.isStructureChunk(state, pos.x, pos.z));
            if (!verified.equals(expected)) throw new AssertionError("Selected-site search omitted native Sky Village candidates");
            var row = new JsonObject(); row.addProperty("x", x); row.addProperty("z", z);
            row.addProperty("nativeGridChecks", checked); row.addProperty("plannedCandidates", planned.size());
            row.addProperty("nativeEligible", expected.size()); cases.add(row);
        }
        var result = new JsonObject(); result.addProperty("candidateSetsEquivalent", true); result.add("cases", cases);
        return result;
    }
    private static long distance(net.minecraft.core.BlockPos point, int x, int z) {
        long dx = (long)point.getX() - x, dz = (long)point.getZ() - z; return dx * dx + dz * dz;
    }
}
