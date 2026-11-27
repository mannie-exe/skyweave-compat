package design.inherent.skyweavecompat.mixin.client;

import dev.ryanhcode.sable.sublevel.render.vanilla.VanillaChunkedSubLevelRenderData;
import net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Retire incompatible compiled data while the original owner schedules its replacement. */
@Mixin(value = VanillaChunkedSubLevelRenderData.class, remap = false)
abstract class ConstructMaterialReloadMixin {
    @Inject(method = "rebuild", at = @At("HEAD"), require = 1)
    private void skyweave$invalidateMaterials(CallbackInfo ci) {
        if (!WorldRenderingSettings.INSTANCE.isReloadRequired()) return;
        for (var section : ((VanillaChunkedSubLevelRenderData)(Object)this).allRenderSections())
            ((ConstructMaterialSectionAccess)section).skyweave$resetMaterial();
    }
}
