package design.inherent.skyweavecompat.clientprobe.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import design.inherent.skyweavecompat.clientprobe.ClientPoseProbe;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Observes the actual vanilla cape geometry call; does not substitute a helper or renderer. */
@Mixin(CapeLayer.class)
abstract class PoseCapeDrawProbeMixin {
    @WrapOperation(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;FFFFFF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/PlayerModel;renderCloak(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;II)V"), require = 1, allow = 1)
    private void pose$capeDraw(PlayerModel<?> model, PoseStack pose, VertexConsumer vertices, int light, int overlay,
            Operation<Void> original, @Local(argsOnly = true) AbstractClientPlayer player) {
        original.call(model, pose, vertices, light, overlay);
        ClientPoseProbe.drewCape(player.getUUID());
    }
}
