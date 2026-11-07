package design.inherent.skyweavecompat.terrain.structure;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.sathrek.sky_archipelago.worldgen.generator.structure.StructurePlacementPassContext;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.PlacementRequest;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.StructureGenerationAttemptService;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.model.HostQuery;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement.*;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.policy.*;
import org.sathrek.sky_archipelago.worldgen.structure.ResolvedStructureSupportPlane;
import org.sathrek.sky_archipelago.worldgen.structure.StructurePlacementCategory;

/** The dedicated igloo route regenerates its top, ladder and basement together at the selected anchor. */
final class IglooLayoutPlanning {
    private IglooLayoutPlanning() {}

    static StructureLayout fit(StructurePlacementPassContext pass, StructureSelection.Site site,
            StructureSet.StructureSelectionEntry entry, StructureStart initial, ResolvedStructureSupportPlane plane,
            PlacementRequest.StructureGenerationContext generation, StructurePlacementCategory category) {
        var id = entry.structure().unwrapKey().orElseThrow().location();
        var route = category == StructurePlacementCategory.DEFAULT ? StructurePlacementCategory.MEDIUM_GROUND : category;
        var policy = StructurePlacementPolicies.forCategory(route);
        if (policy == null || !initial.isValid() || plane == null) return null;
        var query = new HostQuery(id, route, pass.chunkPos(), pass.chunkPos().getMiddleBlockX(), pass.chunkPos().getMiddleBlockZ(),
                StructureSearchRadiusResolver.resolve(policy, pass.settings()), policy);
        var previews = new IslandHostIndex().previewsFor(query, pass.islandField(), pass.settings());
        var hosts = new StructureHostSelector().selectHosts(query, previews, plane.effectiveFootprint(), pass.islandField(), pass.settings());
        if (hosts.hosts().isEmpty()) return LayoutPlanning.rejected(id, "igloo_no_host");
        for (var host : hosts.hosts()) {
            var selected = new IslandSiteSelector(new StructureOverlapGuard()).selectSite(query, host, policy, route,
                    plane.rawFootprint(), plane.effectiveFootprint(), pass.islandField(), pass.settings(), pass.levelSeed());
            if (!selected.successful()) continue;
            var chunk = new ChunkPos(selected.site().x() >> 4, selected.site().z() >> 4);
            var generated = new StructureGenerationAttemptService().generate(id, entry.structure().value(), generation,
                    pass.levelSeed(), chunk, pass.chunk());
            if (generated.valid() && !generated.structureStart().getPieces().isEmpty())
                return LayoutPlanning.layout(id, site, generated.structureStart(), route == StructurePlacementCategory.SMALL_GROUND ? 1 : 2);
        }
        return LayoutPlanning.rejected(id, "igloo_no_site_or_start");
    }
}
