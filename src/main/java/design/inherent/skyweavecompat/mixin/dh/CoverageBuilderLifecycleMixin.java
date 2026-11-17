package design.inherent.skyweavecompat.mixin.dh;

import design.inherent.skyweavecompat.rendering.CoverageCarrier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.LodQuadBuilder", remap = false)
abstract class CoverageBuilderLifecycleMixin {
    @Inject(method = "close", at = @At("HEAD"), require = 1)
    private void skyweave$releaseSourceOwner(CallbackInfo ci) { ((CoverageCarrier) this).skyweave$coverage(null); }
}
