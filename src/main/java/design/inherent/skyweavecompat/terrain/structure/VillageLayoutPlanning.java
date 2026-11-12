package design.inherent.skyweavecompat.terrain.structure;

import design.inherent.skyweavecompat.mixin.VillageGroundingResultAccess;
import java.util.List;
import java.util.Map;
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
import org.sathrek.sky_archipelago.worldgen.benchmark.StructureBenchmarkRecorder;

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
                if(assemblies==distribution.villageFitting().assembliesPerSite()) {
                    VillageDecisionTrace.emit((ResourceLocation)null,"planning","ASSEMBLY_BUDGET",Map.of("assemblies",assemblies));break;
                }
                var id = entry.structure().unwrapKey().orElseThrow().location();
                var budget = StructurePlacementBudget.forCategory(
                        org.sathrek.sky_archipelago.worldgen.structure.StructurePlacementCategory.GROUND_VILLAGE);
                var plan = new VillagePreAnchorPlanner().plan(id, source, StructurePlacementPolicies.GROUND_VILLAGE,
                        null, pass.islandField(), pass.settings(), budget);
                if (plan.anchors().isEmpty()) {
                    StructureBenchmarkRecorder.increment("skyweaveVillageFit.no_anchor");
                    VillageDecisionTrace.emit(id,"pre_anchor","NO_ANCHOR",Map.of());continue;
                }
                var host = plan.anchors().getFirst().host();
                var context = new PlacementRequest.StructureGenerationContext(pass.generator(), pass.biomeSource(), pass.registryAccess(),
                        pass.randomState(), pass.templateManager(), 0, budget);
                VillageDecisionTrace.emit(id,"native_generation","BEGIN",Map.of());
                var generated = new StructureGenerationAttemptService().generate(id, entry.structure().value(), context,
                        pass.levelSeed(), source, pass.chunk());
                // Qualified jigsaw callbacks assemble after the native biome predicate. A rejected
                // biome is an eligibility check, not a consumed opportunity to assemble a layout.
                if(generated.valid() || generated.nativeInvalidCause()==StructureGenerationAttemptService.NativeInvalidCause.INVALID_AFTER_BIOME_ACCEPTED)
                    assemblies++;
                if (!generated.valid()) {
                    StructureBenchmarkRecorder.increment("skyweaveVillageFit.native_"+generated.nativeInvalidCause());
                    VillageDecisionTrace.emit(id,"native_generation",generated.outcome()==StructureGenerationAttemptService.Outcome.BUDGET_EXHAUSTED?"GENERATION_BUDGET":"REJECTED",
                            Map.of("nativeOutcome",String.valueOf(generated.outcome()),"nativeCause",String.valueOf(generated.nativeInvalidCause())));continue;
                }
                var fitted = VillageLayoutRepair.assembly(generated.structureStart(), pass.templateManager(), () -> {
                    VillageDecisionTrace.emit(id,"initial_grounding","BEGIN",Map.of("pieces",generated.structureStart().getPieces().size()));
                    Object before = new VillagePieceGroundingPlanner().normalize(generated.structureStart(), source, pass.islandField(), pass.settings());
                    var preTrim = (VillageGroundingResultAccess)before;
                    if (!preTrim.skyweave$accepted()) {
                        StructureBenchmarkRecorder.increment("skyweaveVillageFit.initial_grounding");
                        VillageDecisionTrace.emit(id,"initial_grounding","REJECTED",Map.of());return null;
                    }
                    var trim = new VillageLayoutTrimmer().trim(preTrim.skyweave$start(), source, host);
                    if (!trim.accepted()) {
                        StructureBenchmarkRecorder.increment("skyweaveVillageFit.trimming");
                        VillageDecisionTrace.emit(id,"trimming","REJECTED",Map.of());return null;
                    }
                    VillageDecisionTrace.emit(id,"final_grounding","BEGIN",Map.of("pieces",trim.structureStart().getPieces().size()));
                    Object after = new VillagePieceGroundingPlanner().normalize(trim.structureStart(), source, pass.islandField(), pass.settings());
                    var result = (VillageGroundingResultAccess)after;
                    if (!result.skyweave$accepted() || result.skyweave$start().getPieces().isEmpty()) {
                        StructureBenchmarkRecorder.increment("skyweaveVillageFit.final_grounding");
                        VillageDecisionTrace.emit(id,"final_grounding","REJECTED",Map.of());return null;
                    }
                    return LayoutPlanning.layout(id, site, result.skyweave$start(), 6).withQuality(
                            VillageLayoutRepair.quality(generated.structureStart(), result.skyweave$start(), pass.templateManager()));
                });
                if (fitted != null) {
                    VillageDecisionTrace.emit(id,"planning","ACCEPTED",Map.of("pieces",fitted.pieces().size(),"quality",fitted.quality()));return fitted;
                }
            }
            return null;
        });
    }
}
