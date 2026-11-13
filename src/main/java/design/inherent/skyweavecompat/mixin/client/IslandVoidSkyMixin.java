package design.inherent.skyweavecompat.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.VertexBuffer;
import design.inherent.skyweavecompat.climate.IslandClimate;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelRenderer.class)
public abstract class IslandVoidSkyMixin {
    @Shadow private ClientLevel level;
    @Shadow private VertexBuffer darkBuffer;

    @WrapOperation(method = "renderSky", at = @At(value = "INVOKE", target =
            "Lcom/mojang/blaze3d/vertex/VertexBuffer;drawWithShader(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lnet/minecraft/client/renderer/ShaderInstance;)V"))
    private void sky(VertexBuffer buffer, Matrix4f modelView, Matrix4f projection, ShaderInstance shader, Operation<Void> original) {
        if (buffer != darkBuffer || !IslandClimate.applies(level) || !design.inherent.skyweavecompat.client.RenderingPreferences.VOID_PLANE.get()) original.call(buffer, modelView, projection, shader);
    }
}
