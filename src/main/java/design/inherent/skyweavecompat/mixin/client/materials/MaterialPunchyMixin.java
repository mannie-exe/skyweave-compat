package design.inherent.skyweavecompat.mixin.client.materials;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import design.inherent.skyweavecompat.client.HandMaterials;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.*;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(targets = "punchy.client.render.PunchyArmRenderer", remap = false)
abstract class MaterialPunchyMixin {
    @WrapMethod(method = "renderFirstPerson")
    private static void skyweave$pose(ItemInHandRenderer renderer, LocalPlayer player, float delta, PoseStack pose,
                                      MultiBufferSource buffers, int light, Operation<Void> original) {
        if (HandMaterials.latePose()) { HandMaterials.replay(buffers); return; }
        boolean success = false;
        try { original.call(renderer, player, delta, pose, buffers, light); success = true; }
        finally { HandMaterials.poseFinished(success); }
    }
}
