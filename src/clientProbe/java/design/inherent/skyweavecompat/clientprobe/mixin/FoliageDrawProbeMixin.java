package design.inherent.skyweavecompat.clientprobe.mixin;

import design.inherent.skyweavecompat.clientprobe.ClientFoliageProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.karto.mc2.mc2_interactivefoliage.gpu.GpuFoliageRenderer", remap = false)
public abstract class FoliageDrawProbeMixin {
    @Inject(method = "drawLegacyRuns", at = @At("HEAD"), require = 1, allow = 1)
    private static void skyweave$foliageDraw(CallbackInfo callback) { ClientFoliageProbe.drawn(); }
}
