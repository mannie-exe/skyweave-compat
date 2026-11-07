package design.inherent.skyweavecompat.mixin;

import design.inherent.skyweavecompat.terrain.structure.StructurePlacementScope;
import java.util.List;
import org.sathrek.sky_archipelago.config.SkyIslandSettings;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;
import org.sathrek.sky_archipelago.worldgen.generator.field.model.IslandPreview;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.model.HostQuery;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement.IslandHostIndex;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = IslandHostIndex.class, remap = false)
public abstract class StructurePolicyHostsMixin {
    @Inject(method = "previewsFor(Lorg/sathrek/sky_archipelago/worldgen/generator/structure/v2/common/model/HostQuery;Lorg/sathrek/sky_archipelago/worldgen/generator/field/IslandField;Lorg/sathrek/sky_archipelago/config/SkyIslandSettings;Z)Ljava/util/List;",
            at = @At("HEAD"), cancellable = true)
    private void selectedHost(HostQuery query, IslandField field, SkyIslandSettings settings, boolean intersecting,
            CallbackInfoReturnable<List<IslandPreview>> result) {
        var scope = StructurePlacementScope.current();
        if (scope != null) {
            var destination = scope.destination();
            result.setReturnValue(field.collectIslandPreviewsIntersectingRadius(destination.getMiddleBlockX(),
                    destination.getMiddleBlockZ(), 0, settings).stream().filter(scope::owns).toList());
        }
    }
}
