package design.inherent.skyweavecompat.terrain.structure;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import org.sathrek.sky_archipelago.worldgen.generator.structure.StructurePlacementPassContext;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.PlacementRequest;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.StructureGenerationAttemptService;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.StructurePlacementBudget;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement.IslandAwareStructureAnchorResolver;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement.PreCommitTerrainAnchor;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement.StructureBiomeCandidateEvaluator;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.policy.StructurePlacementPolicies;
import org.sathrek.sky_archipelago.worldgen.structure.StructurePlacementCategory;
import org.sathrek.sky_archipelago.worldgen.structure.PieceAwareSupportPlaneResolver;

/** The native anchor-first ground route, ending before reservations, references, or block placement. */
final class GroundLayoutPlanning {
    private GroundLayoutPlanning() {}

    static StructureLayout fit(StructureDistribution distribution, StructurePlacementPassContext environment,
            StructureFamily family, StructureSelection.Site site, List<StructureSet.StructureSelectionEntry> entries) {
        var pass = LayoutPlanning.at(environment, site);
        return StructurePlacementScope.with(new StructurePlacementScope(distribution, family, pass, true), () -> {
            for (var entry : LayoutPlanning.variants(pass, entries)) {
                var id = entry.structure().unwrapKey().orElseThrow().location();
                var category = pass.settings().advanced().structurePlacementPolicy().categoryFor(id);
                if (category == StructurePlacementCategory.WATER) {
                    var layout = WaterLayoutPlanning.fit(pass, site, entry);
                    if (layout != null) return layout;
                    continue;
                }
                // Sky Archipelago's productive DEFAULT route uses anchor-first for ordinary shipwrecks.
                if (category == StructurePlacementCategory.DEFAULT && !id.equals(ResourceLocation.withDefaultNamespace("shipwreck"))) {
                    var layout = DefaultGroundLayoutPlanning.fit(pass, site, entry);
                    if (layout != null) return layout;
                    continue;
                }
                if (category != StructurePlacementCategory.DEFAULT && category != StructurePlacementCategory.SMALL_GROUND && category != StructurePlacementCategory.MEDIUM_GROUND
                        && category != StructurePlacementCategory.LARGE_GROUND) continue;
                var policy = StructurePlacementPolicies.forCategory(category);
                var biome = StructureBiomeCandidateEvaluator.create(id, category, entry.structure().value(), pass.biomeSource(),
                        pass.randomState(), pass.chunkPos().getMiddleBlockX(),
                        (pass.settings().terrain().minIslandY() + pass.settings().terrain().maxIslandY()) / 2,
                        pass.chunkPos().getMiddleBlockZ(), pass.settings());
                // resolve() is the uncached native operation; a live claim or a regional miss must not select the plan.
                var anchor = new IslandAwareStructureAnchorResolver().resolve(id, category, pass.chunkPos(), policy, null,
                        pass.islandField(), pass.settings(), pass.levelSeed(), biome.asIslandAnchorBiomeFilter("source_biome_allowed_set"));
                if (anchor.isEmpty()) continue;
                var context = new PlacementRequest.StructureGenerationContext(pass.generator(), pass.biomeSource(), pass.registryAccess(),
                        pass.randomState(), pass.templateManager(), 0, StructurePlacementBudget.forCategory(category));
                var generated = new StructureGenerationAttemptService().generate(id, entry.structure().value(), context,
                        pass.levelSeed(), anchor.get().canonicalStartChunk(), pass.chunk());
                if (!generated.valid()) continue;
                var plane = new PieceAwareSupportPlaneResolver().resolve(id, generated.structureStart(),
                        pass.settings().advanced().structurePlacementPolicy().footprintInsetRatioFor(id));
                if (plane.isEmpty()) continue;
                var anchored = new PreCommitTerrainAnchor().alignToHostSurface(id, generated.structureStart(),
                        anchor.get().canonicalStartChunk(), anchor.get().host());
                int padding = category == StructurePlacementCategory.SMALL_GROUND ? 1
                        : category == StructurePlacementCategory.MEDIUM_GROUND ? 2 : category == StructurePlacementCategory.LARGE_GROUND ? 4 : 0;
                if (!anchored.getPieces().isEmpty()) return LayoutPlanning.layout(id, site, anchored, padding);
            }
            return null;
        });
    }
}
