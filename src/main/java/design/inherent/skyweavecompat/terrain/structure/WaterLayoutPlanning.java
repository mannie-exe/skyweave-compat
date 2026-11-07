package design.inherent.skyweavecompat.terrain.structure;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.sathrek.sky_archipelago.worldgen.generator.structure.StructurePlacementPassContext;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.PlacementRequest;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.StructurePlacementBudget;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.water.WaterV2PlacementEngine;
import org.sathrek.sky_archipelago.worldgen.structure.StructurePlacementCategory;

/** Existing WATER semantics, stopping before terrain reservations and start/reference writes. */
final class WaterLayoutPlanning {
    private WaterLayoutPlanning() {}

    static StructureLayout fit(StructurePlacementPassContext pass, StructureSelection.Site site,
            StructureSet.StructureSelectionEntry entry) {
        var id = entry.structure().unwrapKey().orElseThrow().location();
        var context = new PlacementRequest.StructureGenerationContext(pass.generator(), pass.biomeSource(), pass.registryAccess(),
                pass.randomState(), pass.templateManager(), 0, StructurePlacementBudget.forCategory(StructurePlacementCategory.WATER));
        var request = new PlacementRequest(entry.structure().value(), id, StructureStart.INVALID_START, null, null, pass.sectionPos(),
                pass.chunk(), pass.chunkPos(), pass.settings(), pass.islandField(), pass.levelSeed(), null, null, Level.OVERWORLD, context);
        var result = new WaterV2PlacementEngine().placeAtWaterHost(request);
        return result.accepted() && result.structureStart() != null && !result.structureStart().getPieces().isEmpty()
                ? LayoutPlanning.layout(id, site, result.structureStart(), 0) : null;
    }
}
