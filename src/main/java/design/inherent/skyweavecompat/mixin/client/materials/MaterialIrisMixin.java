package design.inherent.skyweavecompat.mixin.client.materials;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import design.inherent.skyweavecompat.client.HandMaterials;
import net.irisshaders.iris.pathways.HandRenderer;
import net.irisshaders.iris.mixinterface.ItemInHandInterface;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = HandRenderer.class, remap = false)
abstract class MaterialIrisMixin {
    @WrapMethod(method = "renderSolid")
    private void skyweave$solid(Matrix4fc matrix, float delta, Camera camera, GameRenderer renderer,
                                WorldRenderingPipeline pipeline, Operation<Void> original) {
        HandMaterials.begin(renderer.itemInHandRenderer);
        try { original.call(matrix, delta, camera, renderer, pipeline); }
        catch (Throwable failure) { HandMaterials.clear(); throw failure; }
    }
    @WrapOperation(method = "renderSolid", at = @At(value = "INVOKE", target =
            "Lnet/irisshaders/iris/mixinterface/ItemInHandInterface;iris$isAnyHandSolid()Z"), require = 1, allow = 1)
    private boolean skyweave$bothMaterials(ItemInHandInterface hands, Operation<Boolean> original) {
        return original.call(hands) || HandMaterials.active();
    }
    @WrapOperation(method = "renderTranslucent", at = @At(value = "INVOKE", target =
            "Lnet/irisshaders/iris/mixinterface/ItemInHandInterface;iris$isAnyHandTranslucent()Z"), require = 1, allow = 1)
    private boolean skyweave$lateMaterial(ItemInHandInterface hands, Operation<Boolean> original) {
        return original.call(hands) || HandMaterials.active();
    }
    @WrapMethod(method = "renderTranslucent")
    private void skyweave$late(Matrix4fc matrix, float delta, Camera camera, GameRenderer renderer,
                               WorldRenderingPipeline pipeline, Operation<Void> original) {
        try { original.call(matrix, delta, camera, renderer, pipeline); }
        finally { HandMaterials.clear(); }
    }
    @Inject(method = "isHandTranslucent", at = @At("RETURN"), cancellable = true, require = 2, allow = 2)
    private void skyweave$glass(ItemStack stack, CallbackInfoReturnable<Boolean> result) {
        if (HandMaterials.split(stack)) result.setReturnValue(true);
    }
}
