package design.inherent.skyweavecompat.mixin.dh;

import design.inherent.skyweavecompat.rendering.CoverageCarrier;
import design.inherent.skyweavecompat.rendering.CoverageColumns;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.LodBufferContainer", remap = false)
abstract class CoverageBufferMixin implements CoverageCarrier {
    @Unique private volatile CoverageColumns skyweave$columns;
    @Override public CoverageColumns skyweave$coverage() { return skyweave$columns; }
    @Override public void skyweave$coverage(CoverageColumns columns) { skyweave$columns = columns; }
    @Inject(method = "close", at = @At("HEAD"), require = 1)
    private void skyweave$forget(CallbackInfo ci) { skyweave$columns = null; }
}
