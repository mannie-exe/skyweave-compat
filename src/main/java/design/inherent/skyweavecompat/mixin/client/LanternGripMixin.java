/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import design.inherent.skyweavecompat.client.LanternGrip;
import design.inherent.skyweavecompat.client.LanternResources;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import punchymoves.client.animation.LocomotionController.ItemPose;
import punchymoves.client.compat.SpatialGuiCompat;

/** Correct only the original Moves item-bone pose for the delivered managed lantern. */
@Mixin(value = ItemInHandLayer.class, priority = 1500)
abstract class LanternGripMixin {
    @WrapOperation(method = "@skyweave:glidermoves(item)", at = @At(value = "INVOKE",
            target = "Lpunchymoves/client/render/HeldItemAnimation;apply(Lcom/mojang/blaze3d/vertex/PoseStack;Lpunchymoves/client/animation/LocomotionController$ItemPose;)V"),
            require = 1, allow = 1, remap = false)
    private void skyweave$lanternPose(PoseStack poses, ItemPose itemPose, Operation<Void> nativeApply,
            @Local(argsOnly = true) LivingEntity entity, @Local(argsOnly = true) boolean left,
            @Local(index = 16) ItemStack delivered) {
        if (entity instanceof AbstractClientPlayer player && LanternResources.owns(delivered)
                && !SpatialGuiCompat.hidesEquippedItems(player)
                && !(SpatialGuiCompat.ownsCamera() && SpatialGuiCompat.posesAvatar(player))
                && ((ItemInHandLayer<?, ?>) (Object) this).getParentModel() instanceof HumanoidModel<?> model) {
            var corrected = LanternGrip.pose(left ? model.leftArm : model.rightArm, itemPose);
            if (corrected.rotation() != null) itemPose = corrected;
        }
        nativeApply.call(poses, itemPose);
    }
}
