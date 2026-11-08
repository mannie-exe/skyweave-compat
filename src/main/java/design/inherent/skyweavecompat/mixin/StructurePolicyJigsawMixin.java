package design.inherent.skyweavecompat.mixin;

import design.inherent.skyweavecompat.terrain.structure.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.sathrek.sky_archipelago.worldgen.generator.structure.StructureRoutingPolicy;
import org.sathrek.sky_archipelago.worldgen.structure.StructurePlacementCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = StructureRoutingPolicy.class, remap = false)
public abstract class StructurePolicyJigsawMixin {
    @Inject(method = "shouldRouteVillagePreAnchor(Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/world/level/levelgen/structure/Structure;Lorg/sathrek/sky_archipelago/worldgen/structure/StructurePlacementCategory;)Z",
            at = @At("HEAD"), cancellable = true)
    private static void customVillage(ResourceLocation id, Structure structure, StructurePlacementCategory category,
            CallbackInfoReturnable<Boolean> result) {
        if (skyweave$ownsVillage(id, structure, category)) result.setReturnValue(true);
    }

    @Inject(method = "effectiveCategoryForGenerationStep(Lnet/minecraft/world/level/levelgen/structure/Structure;Lnet/minecraft/resources/ResourceLocation;Lorg/sathrek/sky_archipelago/worldgen/structure/StructurePlacementCategory;)Lorg/sathrek/sky_archipelago/worldgen/structure/StructurePlacementCategory;",
            at = @At("HEAD"), cancellable = true)
    private static void preserveVillageCategory(Structure structure, ResourceLocation id, StructurePlacementCategory category,
            CallbackInfoReturnable<StructurePlacementCategory> result) {
        if (skyweave$ownsVillage(id, structure, category)) result.setReturnValue(category);
    }

    @Unique private static boolean skyweave$ownsVillage(ResourceLocation id, Structure structure, StructurePlacementCategory category) {
        var scope = StructurePlacementScope.current();
        return scope != null && scope.family() == StructureFamily.VILLAGES && structure instanceof IslandJigsaw
                && scope.distribution().family(id) == StructureFamily.VILLAGES && category == StructurePlacementCategory.GROUND_VILLAGE;
    }
}
