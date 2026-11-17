package design.inherent.skyweavecompat.mixin.client;

import design.inherent.skyweavecompat.client.ConstructCoverage;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
abstract class ConstructCoverageLifecycleMixin {
    @Inject(method = "renderLevel", at = @At("HEAD"), require = 1)
    private void skyweave$frame(CallbackInfo ci) { ConstructCoverage.beginFrame(); }
    @Inject(method = {"allChanged", "setLevel", "close"}, at = @At("HEAD"), require = 3, allow = 3)
    private void skyweave$invalidate(CallbackInfo ci) { ConstructCoverage.clear(); }
}
