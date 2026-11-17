package design.inherent.skyweavecompat.clientprobe.mixin;

import design.inherent.skyweavecompat.clientprobe.ClientCoverageProbe;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "dev.ryanhcode.sable.sublevel.render.vanilla.VanillaChunkedSubLevelRenderData", remap = false)
abstract class CoverageDrawProbeMixin {
    @Shadow @Final private ClientSubLevel subLevel;
    @Inject(method = "renderChunkedSubLevel", at = @At("HEAD"), require = 1)
    private void coverage$draw(RenderType layer, ShaderInstance shader, Matrix4f view, double x, double y, double z, CallbackInfo ci) {
        if (layer == RenderType.solid() && !ClientCoverageProbe.shadow())
            ClientCoverageProbe.draw(subLevel.getUniqueId());
    }
}
