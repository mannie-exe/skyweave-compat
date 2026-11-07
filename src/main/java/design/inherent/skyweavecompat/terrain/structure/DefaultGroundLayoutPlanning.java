package design.inherent.skyweavecompat.terrain.structure;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import org.sathrek.sky_archipelago.worldgen.generator.structure.StructurePlacementPassContext;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.PlacementRequest;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.StructureGenerationAttemptService;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.StructurePlacementBudget;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement.*;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.policy.StructurePlacementPolicies;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.ground.PreStartHostIslandRetryPlanner;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.village.StructureHostBoundsEvaluator;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.ground.IglooPlacementEngine;
import org.sathrek.sky_archipelago.worldgen.structure.PieceAwareSupportPlaneResolver;
import org.sathrek.sky_archipelago.worldgen.structure.StructurePlacementCategory;
import org.sathrek.sky_archipelago.worldgen.structure.advisor.StructurePlacementRequirements;

/** The native catalog-sized DEFAULT route, preserving bounds and full-footprint grounding checks. */
final class DefaultGroundLayoutPlanning {
    private DefaultGroundLayoutPlanning() {}

    static StructureLayout fit(StructurePlacementPassContext pass, StructureSelection.Site site,
            StructureSet.StructureSelectionEntry entry) {
        var id = entry.structure().unwrapKey().orElseThrow().location();
        var structure = entry.structure().value();
        var size = pass.islandField().structureSize(structure, pass.registryAccess());
        var policy = StructurePlacementRequirements.policy(size, StructurePlacementPolicies.GROUND_DEFAULT);
        var footprint = StructurePlacementRequirements.footprint(size, pass.chunkPos().getMiddleBlockX(), pass.chunkPos().getMiddleBlockZ());
        var biome = StructureBiomeCandidateEvaluator.create(id, StructurePlacementCategory.DEFAULT, structure, pass.biomeSource(),
                pass.randomState(), pass.chunkPos().getMiddleBlockX(),
                (pass.settings().terrain().minIslandY() + pass.settings().terrain().maxIslandY()) / 2,
                pass.chunkPos().getMiddleBlockZ(), pass.settings());
        var gate = new DefaultGroundBiomeProfileGate(biome, pass.levelSeed(), Level.OVERWORLD, pass.islandField(), pass.settings());
        var budget = StructurePlacementBudget.limited(Integer.MAX_VALUE, 64, pass.settings().defaultSurface().maxSiteOffsets(), Integer.MAX_VALUE);
        var plan = new PreStartHostIslandRetryPlanner().plan(id, StructurePlacementCategory.DEFAULT, policy, footprint, pass.settings(),
                pass.islandField(), pass.chunkPos(), pass.levelSeed(), budget, gate, host -> true);
        if (plan.anchors().isEmpty()) return LayoutPlanning.rejected(id, "no_anchor." + plan.failureDiagnostics().phase());
        var generation = new PlacementRequest.StructureGenerationContext(pass.generator(), pass.biomeSource(), pass.registryAccess(),
                pass.randomState(), pass.templateManager(), 0, StructurePlacementBudget.forCategory(StructurePlacementCategory.DEFAULT));
        for (var anchor : plan.anchors()) {
            var generated = new StructureGenerationAttemptService().generate(id, structure, generation, pass.levelSeed(), anchor.chunkPos(), pass.chunk());
            if (!generated.valid()) { LayoutPlanning.rejected(id, "native_invalid"); continue; }
            var start = new PreCommitTerrainAnchor().alignToHostSurface(id, generated.structureStart(), anchor.chunkPos(), anchor.host());
            if (!new StructureHostBoundsEvaluator().fits(anchor.host(), start.getBoundingBox())) {
                LayoutPlanning.rejected(id, "bounds"); continue;
            }
            var plane = new PieceAwareSupportPlaneResolver().resolve(id, start, pass.settings().advanced().structurePlacementPolicy().footprintInsetRatioFor(id));
            if (plane.isEmpty() || !new HostFootprintSupportValidator().evaluate(anchor.host(), plane.get(), pass.islandField(), pass.settings(),
                    policy.groundedRatio(), policy.maxTopVariation(), start).accepted()) {
                LayoutPlanning.rejected(id, "footprint_support"); continue;
            }
            if (id.equals(net.minecraft.resources.ResourceLocation.withDefaultNamespace("desert_pyramid"))
                    && !pyramidBodySupports(pass, start.getBoundingBox())) {
                LayoutPlanning.rejected(id, "pyramid_void_foundation"); continue;
            }
            var category = pass.settings().advanced().structurePlacementPolicy().effectiveCategoryFor(id, plane.get());
            if (IglooPlacementEngine.supports(id))
                return IglooLayoutPlanning.fit(pass, site, entry, start, plane.get(), generation, category);
            int padding = category == StructurePlacementCategory.SMALL_GROUND ? 1
                    : category == StructurePlacementCategory.MEDIUM_GROUND ? 2 : 4;
            return LayoutPlanning.layout(id, site, start, padding);
        }
        return LayoutPlanning.rejected(id, "no_valid_layout");
    }
    /** Vanilla fills foundations downward; an empty edge column otherwise becomes a pillar to the void floor. */
    private static boolean pyramidBodySupports(StructurePlacementPassContext pass,
            net.minecraft.world.level.levelgen.structure.BoundingBox bounds) {
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
            var column = pass.islandField().sampleColumn(x, z, pass.settings());
            // Native trap and chest floors reach fourteen blocks below the accepted template origin.
            if (!column.exists() || column.bottomY() > bounds.minY() - 14) return false;
        }
        return true;
    }

}
