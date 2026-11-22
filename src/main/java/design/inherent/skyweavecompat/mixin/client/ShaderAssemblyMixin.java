package design.inherent.skyweavecompat.mixin.client;

import design.inherent.skyweavecompat.client.ShaderAssembly;
import net.irisshaders.iris.Iris;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.irisshaders.iris.Iris", remap = false)
public abstract class ShaderAssemblyMixin {
    @Inject(method = "loadShaderpack", at = @At("HEAD"), cancellable = true, require = 1)
    private static void skyweave$assemble(CallbackInfo callback) {
        boolean migrated = ShaderAssembly.prepare();
        if (migrated && Iris.getIrisConfig() != null) {
            try { Iris.getIrisConfig().initialize(); }
            catch (java.io.IOException failure) {
                com.mojang.logging.LogUtils.getLogger().error("Cannot reload migrated shader selection. {}",
                        design.inherent.skyweavecompat.OptionalIntegrations.describe(design.inherent.skyweavecompat.Feature.SHADER_PATCHES), failure);
                callback.cancel(); return;
            }
        }
        if (Iris.getIrisConfig() != null && ShaderAssembly.failed(Iris.getIrisConfig().getShaderPackName().orElse(""))) callback.cancel();
    }
}
