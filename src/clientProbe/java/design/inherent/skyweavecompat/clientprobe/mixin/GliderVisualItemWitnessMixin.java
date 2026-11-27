package design.inherent.skyweavecompat.clientprobe.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import design.inherent.skyweavecompat.clientprobe.ClientGliderVisualProbe;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
abstract class GliderVisualItemWitnessMixin {
    @Inject(method="renderItem",at=@At("HEAD"),require=1,allow=1)
    private void fixture$draw(LivingEntity entity, ItemStack stack, ItemDisplayContext display, boolean left,
            PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        ClientGliderVisualProbe.draw(entity,stack,display,left);
    }
}
