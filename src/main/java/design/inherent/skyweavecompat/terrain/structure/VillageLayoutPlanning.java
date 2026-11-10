package design.inherent.skyweavecompat.terrain.structure;

import design.inherent.skyweavecompat.mixin.VillageGroundingResultAccess;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import org.sathrek.sky_archipelago.worldgen.generator.structure.StructurePlacementPassContext;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.PlacementRequest;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.StructureGenerationAttemptService;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.StructurePlacementBudget;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.policy.StructurePlacementPolicies;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.village.VillageLayoutTrimmer;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.village.VillagePieceGroundingPlanner;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.village.VillagePreAnchorPlanner;

/** Native layout generation and grounding only. No starts, references, reservations, chunks, or blocks are committed. */
final class VillageLayoutPlanning {
    private VillageLayoutPlanning() {}

    static StructureLayout fit(StructureDistribution distribution, StructurePlacementPassContext environment,
            StructureSelection.Site site, List<StructureSet.StructureSelectionEntry> entries) {
        var pass = LayoutPlanning.at(environment, site);
        var source = pass.chunkPos();
        return StructurePlacementScope.with(new StructurePlacementScope(distribution, StructureFamily.VILLAGES, pass, true), () -> {
            int assemblies=0;
            for (var entry : LayoutPlanning.variants(pass, entries).stream().limit(256).toList()) {
                if(assemblies==distribution.villageFitting().assembliesPerSite())break;
                var id = entry.structure().unwrapKey().orElseThrow().location();
                var budget = StructurePlacementBudget.forCategory(
                        org.sathrek.sky_archipelago.worldgen.structure.StructurePlacementCategory.GROUND_VILLAGE);
                var plan = new VillagePreAnchorPlanner().plan(id, source, StructurePlacementPolicies.GROUND_VILLAGE,
                        null, pass.islandField(), pass.settings(), budget);
                if (plan.anchors().isEmpty()) continue;
                var host = plan.anchors().getFirst().host();
                var context = new PlacementRequest.StructureGenerationContext(pass.generator(), pass.biomeSource(), pass.registryAccess(),
                        pass.randomState(), pass.templateManager(), 0, budget);
                var generated = new StructureGenerationAttemptService().generate(id, entry.structure().value(), context,
                        pass.levelSeed(), source, pass.chunk());
                // Qualified jigsaw callbacks assemble after the native biome predicate. A rejected
                // biome is an eligibility check, not a consumed opportunity to assemble a layout.
                if(generated.valid() || generated.nativeInvalidCause()==StructureGenerationAttemptService.NativeInvalidCause.INVALID_AFTER_BIOME_ACCEPTED)
                    assemblies++;
                if (!generated.valid()) continue;
                var fitted = VillageLayoutRepair.assembly(generated.structureStart(), pass.templateManager(), () -> {
                    Object before = new VillagePieceGroundingPlanner().normalize(generated.structureStart(), source, pass.islandField(), pass.settings());
                    var preTrim = (VillageGroundingResultAccess)before;
                    if (!preTrim.skyweave$accepted()) return null;
                    var trim = new VillageLayoutTrimmer().trim(preTrim.skyweave$start(), source, host);
                    if (!trim.accepted()) return null;
                    Object after = new VillagePieceGroundingPlanner().normalize(trim.structureStart(), source, pass.islandField(), pass.settings());
                    var result = (VillageGroundingResultAccess)after;
                    if (!result.skyweave$accepted() || result.skyweave$start().getPieces().isEmpty()) return null;
                    return LayoutPlanning.layout(id, site, result.skyweave$start(), 6).withQuality(
                            VillageLayoutRepair.quality(generated.structureStart(), result.skyweave$start(), pass.templateManager()));
                });
                if (fitted != null) return fitted;
            }
            return null;
        });
    }
}
