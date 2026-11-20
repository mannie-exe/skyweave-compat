package design.inherent.skyweavecompat.mixin.client.definitions;

import design.inherent.skyweavecompat.client.model.ItemDefinitions;
import java.util.Optional;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "timmychips.modefiteitemdefinitions.property.resolver.ItemModelResolver", remap = false)
abstract class DefinitionResolverMixin {
    @Inject(method = "resolveModel", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private static void skyweave$resolve(ResourceLocation id, ItemDisplayContext display, ItemStack stack, LivingEntity entity,
                                         CallbackInfoReturnable<Optional<BakedModel>> cir) {
        cir.setReturnValue(ItemDefinitions.resolve(id, display, stack, entity));
    }
}
