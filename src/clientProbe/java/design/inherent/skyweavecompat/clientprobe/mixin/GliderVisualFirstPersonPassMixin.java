package design.inherent.skyweavecompat.clientprobe.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import design.inherent.skyweavecompat.clientprobe.ClientGliderVisualProbe;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ItemInHandRenderer.class)
abstract class GliderVisualFirstPersonPassMixin {
    @WrapMethod(method="renderHandsWithItems")
    private void fixture$pass(float partialTick,PoseStack pose,MultiBufferSource.BufferSource buffers,LocalPlayer player,int light,Operation<Void> original) {
        design.inherent.skyweavecompat.clientprobe.ClientGliderPresentationProbe.firstPass(()->
                ClientGliderVisualProbe.firstPersonPass(()->original.call(partialTick,pose,buffers,player,light)));
    }
}
