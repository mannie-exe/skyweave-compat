package design.inherent.skyweavecompat.probe;

import com.google.gson.JsonObject;
import design.inherent.skyweavecompat.terrain.structure.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Bounded equivalence checks against real placement/fit owners, outside the timed query. */
final class SearchPlanningProbe {
    static JsonObject verify(ServerLevel level, int x, int z) {
        try {
            long began = System.nanoTime();
            var state = level.getChunkSource().getGeneratorState();
            var distribution = ((StructurePlacementState)state).skyweave$structureDistribution();
            var nativePlacement = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE)
                    .holders().flatMap(holder -> state.getPlacementsForStructure(holder).stream()).filter(ArchipelagoStructurePlacement.class::isInstance)
                    .map(ArchipelagoStructurePlacement.class::cast).findFirst().orElseThrow();
            var owners = new LinkedHashSet<StructureSelection.Owner>();
            int checked = 0;
            for (var family : List.of(StructureFamily.PORTALS, StructureFamily.VILLAGES)) {
                int radius = 8192;
                var direct = new HashSet<ChunkPos>();
                var cells = distribution.searchOwners(x, z, radius, 16);
                while (!cells.complete()) {
                    var cell = cells.next(); var owner = new StructureSelection.Owner(cell.x(), cell.z());
                    for (var site : distribution.searchSites(owner, family)) {
                        var pos = new ChunkPos(site.chunkX(), site.chunkZ());
                        if (!within(x, z, radius, pos) || !distribution.possibleNewStart(family, pos.x, pos.z)) continue;
                        direct.add(pos); owners.add(owner);
                    }
                }
                var exhaustive = new HashSet<ChunkPos>();
                int spacing = nativePlacement.spacing(), cx = Math.floorDiv(Math.floorDiv(x, 16), spacing),
                        cz = Math.floorDiv(Math.floorDiv(z, 16), spacing), rings = Math.ceilDiv(radius + 32, spacing * 16) + 1;
                for (int dx = -rings; dx <= rings; dx++) for (int dz = -rings; dz <= rings; dz++) {
                    var pos = nativePlacement.getPotentialStructureChunk(level.getSeed(), (cx + dx) * spacing, (cz + dz) * spacing);
                    if (within(x, z, radius, pos) && distribution.possibleNewStart(family, pos.x, pos.z)) exhaustive.add(pos);
                }
                if (!direct.equals(exhaustive)) throw new AssertionError("Direct/exhaustive candidate mismatch: " + family
                        + " direct=" + direct.size() + " exhaustive=" + exhaustive.size());
                checked += direct.size();
            }
            if (owners.isEmpty() || checked == 0) throw new AssertionError("Equivalence fixture needs positive candidates");
            var cacheField = StructureDistribution.class.getDeclaredField("fittedPlans"); cacheField.setAccessible(true);
            var cache = (com.google.common.cache.Cache<?, ?>)cacheField.get(distribution);
            var environment = StructureDistribution.class.getDeclaredField("environment"); environment.setAccessible(true);
            if (environment.get(distribution) == null) throw new AssertionError("No native fitting environment");
            int compared = 0;
            for (var owner : owners.stream().limit(2).toList()) {
                cache.invalidate(owner);
                var prefix = distribution.plan(owner, StructureFamily.VILLAGES);
                var incrementallyCompleted = distribution.plan(owner);
                cache.invalidate(owner);
                var eager = distribution.plan(owner);
                if (!incrementallyCompleted.equals(eager)) throw new AssertionError("Eager/lazy native layouts differ: " + owner);
                for (var family : List.of(StructureFamily.PORTALS, StructureFamily.VILLAGES))
                    if (!prefix.sites(family).equals(eager.sites(family))
                            || !Objects.equals(prefix.layouts().get(family), eager.layouts().get(family)))
                        throw new AssertionError("Published prefix changed: " + family);
                cache.invalidate(owner);
                var requests = Arrays.stream(StructureFamily.values()).map(family ->
                        CompletableFuture.supplyAsync(() -> distribution.plan(owner, family))).toList();
                requests.forEach(CompletableFuture::join);
                if (!distribution.plan(owner).equals(eager)) throw new AssertionError("Concurrent/evicted layouts differ: " + owner);
                compared++;
            }
            var result = new JsonObject(); result.addProperty("candidateSetsEqual", true);
            result.addProperty("positiveCandidates", checked); result.addProperty("nativeOwnerPlansCompared", compared);
            result.addProperty("eagerLazyConcurrentEvictionEqual", true);
            result.addProperty("elapsedSeconds", (System.nanoTime() - began) / 1e9);
            return result;
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }
    private static boolean within(int x, int z, int radius, ChunkPos pos) {
        long dx = pos.getMiddleBlockX() - (long)x, dz = pos.getMiddleBlockZ() - (long)z;
        return dx * dx + dz * dz <= (long)radius * radius;
    }
    private SearchPlanningProbe() {}
}
