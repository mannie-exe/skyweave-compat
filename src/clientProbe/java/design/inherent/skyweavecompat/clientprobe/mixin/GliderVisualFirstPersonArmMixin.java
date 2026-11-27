package design.inherent.skyweavecompat.clientprobe.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import design.inherent.skyweavecompat.clientprobe.ClientGliderVisualProbe;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ItemInHandRenderer.class)
abstract class GliderVisualFirstPersonArmMixin {
    @WrapMethod(method="renderArmWithItem")
    private void fixture$arm(AbstractClientPlayer player,float partialTick,float pitch,InteractionHand hand,float swing,
            ItemStack stack,float equipped,PoseStack pose,MultiBufferSource buffers,int light,Operation<Void> original) {
        design.inherent.skyweavecompat.clientprobe.ClientGliderPresentationProbe.handArgument(player,hand,stack);
        ClientGliderVisualProbe.firstPersonAttempt(player,stack);
        original.call(player,partialTick,pitch,hand,swing,stack,equipped,pose,buffers,light);
    }
}
