package design.inherent.skyweavecompat.mixin.client.definitions;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.mojang.blaze3d.vertex.PoseStack;
import design.inherent.skyweavecompat.client.model.ItemDefinitions;
import net.minecraft.client.color.item.ItemColors;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ItemRenderer.class, priority = 1900)
abstract class DefinitionRenderingMixin {
    @WrapMethod(method = "getModel")
    private BakedModel skyweave$lookupContext(ItemStack stack, Level level, LivingEntity entity, int seed, Operation<BakedModel> original) {
        try (var scope = ItemDefinitions.invocation(stack, entity, level, seed)) {
            return original.call(stack, level, entity, seed);
        }
    }
    @WrapMethod(method = "renderStatic(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/level/Level;III)V")
    private void skyweave$context(LivingEntity entity, ItemStack stack, ItemDisplayContext display, boolean left, PoseStack pose,
                                  MultiBufferSource buffers, Level level, int light, int overlay, int seed, Operation<Void> original) {
        try (var scope = ItemDefinitions.invocation(stack, entity, level, seed)) {
            original.call(entity, stack, display, left, pose, buffers, level, light, overlay, seed);
        }
    }
    @WrapMethod(method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V")
    private void skyweave$layers(ItemStack stack, ItemDisplayContext display, boolean left, PoseStack pose,
                                 MultiBufferSource buffers, int light, int overlay, BakedModel model, Operation<Void> original) {
        var renderer = (ItemRenderer) (Object) this;
        if (!ItemDefinitions.render(renderer, stack, display, left, pose, buffers, light, overlay, model))
            original.call(stack, display, left, pose, buffers, light, overlay, ItemDefinitions.currentModel(renderer, stack, model));
    }
    @WrapOperation(method = "renderQuadList", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/color/item/ItemColors;getColor(Lnet/minecraft/world/item/ItemStack;I)I"), require = 1, allow = 1)
    private int skyweave$tint(ItemColors colors, ItemStack stack, int index, Operation<Integer> original) {
        Integer color = ItemDefinitions.color(stack, index);
        return color == null ? original.call(colors, stack, index) : color;
    }
}
