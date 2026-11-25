package design.inherent.skyweavecompat.mixin.client.bow;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import design.inherent.skyweavecompat.client.model.BowModelContext;
import java.util.Optional;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

/** Covers Modefite's native resolver even when Compat's definition compiler is disabled. */
@Mixin(targets = "timmychips.modefiteitemdefinitions.property.resolver.ItemModelResolver", remap = false)
abstract class BowModelContextMixin {
    @WrapMethod(method = "resolveModel")
    private static Optional<BakedModel> skyweave$context(ResourceLocation id, ItemDisplayContext display,
            ItemStack stack, LivingEntity entity, Operation<Optional<BakedModel>> original) {
        return BowModelContext.query(stack, entity, display, () -> original.call(id, display, stack, entity));
    }
}
