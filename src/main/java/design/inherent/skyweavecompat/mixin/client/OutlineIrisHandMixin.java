package design.inherent.skyweavecompat.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import design.inherent.skyweavecompat.client.IrisHandEffects;
import net.irisshaders.iris.pathways.HandRenderer;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = HandRenderer.class, remap = false)
public abstract class OutlineIrisHandMixin {
    @WrapMethod(method = {"renderSolid", "renderTranslucent"})
    private void skyweave$handPass(Matrix4fc matrix, float tickDelta, Camera camera, GameRenderer renderer,
            WorldRenderingPipeline pipeline, Operation<Void> original) {
        try (var pass = IrisHandEffects.pass(pipeline)) {
            original.call(matrix, tickDelta, camera, renderer, pipeline);
            pass.succeeded();
        }
    }

    @Inject(method = {"renderSolid", "renderTranslucent"}, at = @At(value = "FIELD",
            target = "Lnet/irisshaders/iris/pathways/HandRenderer;ACTIVE:Z", opcode = org.objectweb.asm.Opcodes.PUTFIELD,
            ordinal = 0, shift = At.Shift.AFTER), require = 2, allow = 2)
    private void skyweave$eligibleHand(Matrix4fc matrix, float tickDelta, Camera camera, GameRenderer renderer,
            WorldRenderingPipeline pipeline, CallbackInfo callback) { IrisHandEffects.enter(pipeline); }
}
