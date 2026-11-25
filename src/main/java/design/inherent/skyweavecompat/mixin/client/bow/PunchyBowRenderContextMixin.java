package design.inherent.skyweavecompat.mixin.client.bow;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import design.inherent.skyweavecompat.client.model.BowModelContext;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;

/** Punchy's first-person draw intentionally requests third-person item transforms. */
@Mixin(targets = "punchy.client.render.PunchyArmRenderer", remap = false)
abstract class PunchyBowRenderContextMixin {
    @WrapMethod(method = "renderItemInHand")
    private static void skyweave$origin(ItemInHandRenderer renderer, PlayerModel<?> model, LocalPlayer player,
            HumanoidArm arm, PoseStack pose, MultiBufferSource buffers, int light, Matrix4f transform,
            ModelPart hand, ModelPart sleeve, ModelPart item, float partialTick, Operation<Void> original) {
        BowModelContext.fromPunchy(player, arm, () -> original.call(renderer, model, player, arm, pose, buffers,
                light, transform, hand, sleeve, item, partialTick));
    }
}
