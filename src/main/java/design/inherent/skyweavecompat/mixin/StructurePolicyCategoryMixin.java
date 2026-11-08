package design.inherent.skyweavecompat.mixin;

import design.inherent.skyweavecompat.terrain.structure.StructureWorldScope;
import net.minecraft.resources.ResourceLocation;
import org.sathrek.sky_archipelago.worldgen.structure.StructurePlacementCategory;
import org.sathrek.sky_archipelago.worldgen.structure.StructurePlacementPolicy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Tagged routes are local to one owned placement pass; explicit policy overrides stay authoritative. */
@Mixin(value = StructurePlacementPolicy.class, remap = false)
public abstract class StructurePolicyCategoryMixin {
    @Inject(method = "categoryFor", at = @At("HEAD"), cancellable = true)
    private void selectedCategory(ResourceLocation id, CallbackInfoReturnable<StructurePlacementCategory> result) {
        var owner = StructureWorldScope.current();
        var category = owner == null ? null : owner.category(id, this);
        if (category != null) result.setReturnValue(category);
    }
}
