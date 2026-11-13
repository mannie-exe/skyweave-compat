package design.inherent.skyweavecompat.mixin.client;

import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraft.client.renderer.entity.DisplayRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Omit the text and its background only while Iris builds the shadow map. */
@Mixin(DisplayRenderer.TextDisplayRenderer.class)
public abstract class TextDisplayShadowMixin {
    @Inject(method = "renderInner(Lnet/minecraft/world/entity/Display$TextDisplay;Lnet/minecraft/world/entity/Display$TextDisplay$TextRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V",
            at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void skyweave$skipTextDisplayShadow(CallbackInfo callback) {
        if (design.inherent.skyweavecompat.client.RenderingPreferences.TEXT_SHADOW.get() && IrisApi.getInstance().isRenderingShadowPass()) callback.cancel();
    }
}
