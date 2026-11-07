package design.inherent.skyweavecompat.terrain.structure;

import java.util.List;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.sathrek.sky_archipelago.worldgen.generator.structure.StructurePlacementPassContext;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.PlacementRequest;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.StructurePlacementBudget;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.underground.*;
import org.sathrek.sky_archipelago.worldgen.structure.StructurePlacementCategory;

/** Native underground search and fit, with the final committer reduced to its pure vertical shift. */
final class UndergroundLayoutPlanning {
    private UndergroundLayoutPlanning() {}

    static StructureLayout fit(StructureDistribution distribution, StructurePlacementPassContext environment,
            StructureFamily family, StructureSelection.Site site, List<StructureSet.StructureSelectionEntry> entries) {
        var pass = LayoutPlanning.at(environment, site);
        return StructurePlacementScope.with(new StructurePlacementScope(distribution, family, pass, true), () -> {
            for (var entry : LayoutPlanning.variants(pass, entries)) {
                var id = entry.structure().unwrapKey().orElseThrow().location();
                var structure = entry.structure().value();
                var behavior = pass.settings().advanced().structurePlacementPolicy().undergroundBehaviorFor(id, structure);
                var strategy = new UndergroundStrategySelector().select(behavior, id, structure);
                var context = new PlacementRequest.StructureGenerationContext(pass.generator(), pass.biomeSource(), pass.registryAccess(),
                        pass.randomState(), pass.templateManager(), 0, StructurePlacementBudget.forCategory(StructurePlacementCategory.UNDERGROUND),
                        new UndergroundSearchContext(UndergroundWorkBudget.forPlacement(id, strategy)));
                var request = new PlacementRequest(structure, id, StructureStart.INVALID_START, null, null, pass.sectionPos(),
                        pass.chunk(), pass.chunkPos(), pass.settings(), pass.islandField(), pass.levelSeed(), null, null, Level.OVERWORLD, context);
                var result = new UndergroundV2PlacementEngine().place(request);
                if (result.accepted() && result.structureStart() != null && !result.structureStart().getPieces().isEmpty())
                    return LayoutPlanning.layout(id, site, result.structureStart(), 0);
            }
            return null;
        });
    }
}
