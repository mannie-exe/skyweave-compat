package design.inherent.skyweavecompat.terrain.structure;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.resources.ResourceLocation;
import org.sathrek.sky_archipelago.worldgen.benchmark.StructureBenchmarkRecorder;
import org.sathrek.sky_archipelago.worldgen.structure.RuinedPortalPlacementPolicy;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.sathrek.sky_archipelago.worldgen.generator.structure.StructurePlacementPassContext;

/** Read-only inputs shared by the native layout planners. */
final class LayoutPlanning {
    private LayoutPlanning() {}

    static StructureLayout rejected(ResourceLocation id, String stage) {
        StructureBenchmarkRecorder.increment("skyweaveStructureFit." + id + "." + stage);
        return null;
    }

    static StructurePlacementPassContext at(StructurePlacementPassContext environment, StructureSelection.Site site) {
        var source = new ChunkPos(site.chunkX(), site.chunkZ());
        var chunk = new ProtoChunk(source, UpgradeData.EMPTY, environment.chunk(),
                environment.registryAccess().registryOrThrow(Registries.BIOME), null);
        return new StructurePlacementPassContext(environment.generator(), environment.biomeSource(), environment.registryAccess(),
                environment.structureState(), null, chunk, environment.templateManager(), environment.settings(), environment.islandField(),
                environment.levelSeed(), environment.randomState(), source, SectionPos.bottomOf(chunk));
    }

    /** The pipeline removes failed alternatives from the same weighted draw. */
    static List<StructureSet.StructureSelectionEntry> variants(StructurePlacementPassContext pass,
            List<StructureSet.StructureSelectionEntry> entries) {
        var owner = StructureWorldScope.current();
        var remaining = new ArrayList<>(entries.stream().filter(entry -> owner == null || !owner.suppresses(entry.structure().value())).toList());
        var result = new ArrayList<StructureSet.StructureSelectionEntry>();
        var random = new WorldgenRandom(new LegacyRandomSource(0));
        random.setLargeFeatureSeed(pass.levelSeed(), pass.chunkPos().x, pass.chunkPos().z);
        int totalWeight = remaining.stream().mapToInt(StructureSet.StructureSelectionEntry::weight).sum();
        while (!remaining.isEmpty()) {
            int ticket = random.nextInt(totalWeight), index = 0;
            while ((ticket -= remaining.get(index).weight()) >= 0) index++;
            var entry = remaining.remove(index);
            totalWeight -= entry.weight();
            result.add(entry);
        }
        return result.stream().filter(entry -> {
            var id = entry.structure().unwrapKey().orElseThrow().location();
            return StructureMembership.family(entry.structure()) != null && !pass.settings().advanced().structurePlacementPolicy().isDenied(id);
        }).toList();
    }

    static List<StructureVolume.Box> pieces(StructureStart start) {
        return start.getPieces().stream().map(piece -> box(piece.getBoundingBox())).toList();
    }

    static StructureLayout layout(ResourceLocation id, StructureSelection.Site site, StructureStart start, int padding) {
        var bounds = start.getBoundingBox();
        var scope = StructurePlacementScope.current();
        var layout = new StructureLayout(id, site, pieces(start), padding, box(bounds), box(RuinedPortalPlacementPolicy.impactBounds(id, bounds)),
                scope == null ? StructureFamily.forId(id.toString()) : scope.family());
        // Jigsaw expansion padding is an assembly reservation, not a persisted
        // template envelope. Recognize the exact reload envelope without shrinking
        // native conflict reservations or matching arbitrary nearby bounds.
        return scope == null ? layout : layout.withPersistedBounds(
                box(PhysicalStructureIdentity.referenceBounds(start, scope.pass().templateManager())));
    }

    private static StructureVolume.Box box(BoundingBox bounds) {
        return new StructureVolume.Box(bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ());
    }
}
