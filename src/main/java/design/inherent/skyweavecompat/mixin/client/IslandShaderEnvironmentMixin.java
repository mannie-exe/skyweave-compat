package design.inherent.skyweavecompat.mixin.client;

import design.inherent.skyweavecompat.Feature;
import design.inherent.skyweavecompat.OptionalIntegrations;
import design.inherent.skyweavecompat.climate.IslandClimate;
import net.irisshaders.iris.gl.uniform.UniformHolder;
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import net.irisshaders.iris.shaderpack.properties.PackDirectives;
import net.irisshaders.iris.uniforms.CommonUniforms;
import net.irisshaders.iris.uniforms.FrameUpdateNotifier;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Generated shader recipes use an explicit world policy rather than guessing from altitude. */
@Mixin(value = CommonUniforms.class, remap = false)
public abstract class IslandShaderEnvironmentMixin {
    @Inject(method = "generalCommonUniforms", at = @At("HEAD"))
    private static void environment(UniformHolder uniforms, FrameUpdateNotifier notifier, PackDirectives directives,
            CallbackInfo callback) {
        uniforms.uniform1i(UniformUpdateFrequency.PER_FRAME, "skyweaveWorld", () -> {
            var level = Minecraft.getInstance().level;
            return level != null && IslandClimate.applies(level) ? 1 : 0;
        }).uniform1i(UniformUpdateFrequency.PER_FRAME, "skyweaveVoidVisuals", () -> {
            var level = Minecraft.getInstance().level;
            return level != null && IslandClimate.applies(level) && design.inherent.skyweavecompat.client.RenderingPreferences.enabled(Feature.ISLAND_VOID,
                    design.inherent.skyweavecompat.client.RenderingPreferences.VOID_FOG) ? 1 : 0;
        }).uniform1i(UniformUpdateFrequency.PER_FRAME, "skyweaveOpenSky",
                design.inherent.skyweavecompat.client.IslandSkyExposure::openSky);
    }
}
