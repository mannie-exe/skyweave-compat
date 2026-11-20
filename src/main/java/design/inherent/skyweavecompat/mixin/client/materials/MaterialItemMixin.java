package design.inherent.skyweavecompat.mixin.client.materials;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.*;
import design.inherent.skyweavecompat.client.HandMaterials;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.*;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = ItemRenderer.class, priority = 2000)
abstract class MaterialItemMixin {
    @WrapMethod(method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V")
    private void skyweave$draw(ItemStack stack, ItemDisplayContext display, boolean left, PoseStack pose,
                               MultiBufferSource buffers, int light, int overlay, BakedModel model, Operation<Void> original) {
        if (!HandMaterials.handPass()) { original.call(stack, display, left, pose, buffers, light, overlay, model); return; }
        if (!HandMaterials.capture((ItemRenderer) (Object) this, stack, display, left, pose, light, overlay, model)) {
            try (var scope = HandMaterials.drawing(stack, model)) {
                original.call(stack, display, left, pose, buffers, light, overlay, model);
            }
        }
    }
    @WrapMethod(method = "renderModelLists")
    private void skyweave$material(BakedModel model, ItemStack stack, int light, int overlay, PoseStack pose,
                                   VertexConsumer vertices, Operation<Void> original) {
        original.call(HandMaterials.layer(model, stack), stack, light, overlay, pose, vertices);
    }
}
