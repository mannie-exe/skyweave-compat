package design.inherent.skyweavecompat.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import design.inherent.skyweavecompat.terrain.structure.StructurePlacementScope;
import java.util.List;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.model.HostIsland;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement.IslandSiteSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = IslandSiteSelector.class, remap = false)
public abstract class StructurePolicySitesMixin {
    @ModifyVariable(method = {"scanOffsets", "preScoreDefaultOffsets"}, at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 2)
    private List<IslandSiteSelector.Offset> selectedSite(List<IslandSiteSelector.Offset> offsets,
            @Local(argsOnly = true) HostIsland host) {
        var scope = StructurePlacementScope.current();
        if (scope == null) return offsets;
        if (!scope.owns(host.preview())) return List.of();
        var destination = scope.destination();
        return List.of(new IslandSiteSelector.Offset(destination.getMiddleBlockX() - host.preview().x(),
                destination.getMiddleBlockZ() - host.preview().z()));
    }
}
