package design.inherent.skyweavecompat.clientprobe.mixin;

import com.mojang.blaze3d.vertex.VertexBuffer;
import design.inherent.skyweavecompat.clientprobe.ClientHandEffectsProbe;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Actual native draw boundary. This opt-in tracing is excluded from performance comparisons. */
@Mixin(VertexBuffer.class)
public abstract class HandEffectDrawMixin {
    @Inject(method = "_drawWithShader", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/VertexBuffer;draw()V"), require = 1, allow = 1)
    private void skyweave$effectDraw(Matrix4f view, Matrix4f projection, ShaderInstance shader, CallbackInfo callback) {
        ClientHandEffectsProbe.draw(shader);
    }
}
