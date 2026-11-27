package design.inherent.skyweavecompat.mixin.client;

import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Flush single-block geometry while Iris still owns the corresponding terrain pass. */
@Mixin(targets = "dev.ryanhcode.sable.sublevel.render.dispatcher.VanillaSubLevelRenderDispatcher", remap = false)
abstract class SingleBlockPassMixin {
    // The exact native tessellator reads this argument only through Matrix4f.mul.
    // Its body transform and camera translation remain native; Iris applies the view rotation on the GPU.
    @Unique private static final Matrix4f SKYWEAVE_CAMERA_RELATIVE = new Matrix4f();

    @Shadow public abstract void renderAfterSections(Iterable<ClientSubLevel> levels,
            double x, double y, double z, Matrix4f modelView, Matrix4f projection, float partialTick);

    @Inject(method = "renderSectionLayer", at = @At("TAIL"), require = 1, allow = 1)
    private void skyweave$flushCurrentPass(Iterable<ClientSubLevel> levels, RenderType layer,
            ShaderInstance shader, double x, double y, double z, Matrix4f modelView,
            Matrix4f projection, float partialTick, CallbackInfo ci) {
        if (IrisApi.getInstance().isShaderPackInUse())
            renderAfterSections(levels, x, y, z, modelView, projection, partialTick);
    }

    @ModifyArg(method = "renderAfterSections", at = @At(value = "INVOKE",
            target = "Ldev/ryanhcode/sable/sublevel/render/vanilla/VanillaSingleSubLevelRenderData;renderSingleBlock(Lnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lorg/joml/Matrix4f;DDD)V"),
            index = 2, require = 1, allow = 1)
    private Matrix4f skyweave$singleCameraView(Matrix4f nativeView) {
        return IrisApi.getInstance().isShaderPackInUse() ? SKYWEAVE_CAMERA_RELATIVE : nativeView;
    }
}
