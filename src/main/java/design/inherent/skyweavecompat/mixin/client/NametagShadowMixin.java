package design.inherent.skyweavecompat.mixin.client;

import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraft.client.renderer.entity.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep entity geometry in shadows while omitting the base renderer's nametag. */
// Run before Iris's priority-1000 nametag material setup; cancellation must not bypass its reset.
@Mixin(value = EntityRenderer.class, priority = 1100)
public abstract class NametagShadowMixin {
    @Inject(method = "renderNameTag(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V",
            at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void skyweave$skipNametagShadow(CallbackInfo callback) {
        if (design.inherent.skyweavecompat.client.RenderingPreferences.NAMETAG_SHADOW.get() && IrisApi.getInstance().isRenderingShadowPass()) callback.cancel();
    }
}
