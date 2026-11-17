package design.inherent.skyweavecompat.mixin.client;

import design.inherent.skyweavecompat.client.IrisHandEffects;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = IrisRenderingPipeline.class, remap = false)
public abstract class OutlineIrisLifecycleMixin {
    @Inject(method = "beginLevelRendering", at = @At("HEAD"), require = 1, allow = 1)
    private void skyweave$beginEffects(CallbackInfo callback) { IrisHandEffects.begin(this); }

    @Inject(method = "finalizeLevelRendering", at = @At("RETURN"), require = 1, allow = 1)
    private void skyweave$finishEffects(CallbackInfo callback) { IrisHandEffects.finish(this); }

    @Inject(method = "destroy", at = @At("HEAD"), require = 1, allow = 1)
    private void skyweave$clearEffects(CallbackInfo callback) { IrisHandEffects.clear(this); }
}
