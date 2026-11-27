package design.inherent.skyweavecompat.clientprobe.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import design.inherent.skyweavecompat.clientprobe.ClientGliderVisualProbe;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=ItemRenderer.class,priority=3000)
abstract class GliderVisualModelWitnessMixin {
    @com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(method="render")
    private void fixture$scope(ItemStack stack,ItemDisplayContext display,boolean left,PoseStack pose,
            MultiBufferSource buffers,int light,int overlay,BakedModel model,
            com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original) {
        design.inherent.skyweavecompat.clientprobe.ClientGliderPresentationProbe.model(stack,display,()->
                original.call(stack,display,left,pose,buffers,light,overlay,model));
    }
    @Inject(method="render",at=@At("HEAD"),require=1,allow=1)
    private void fixture$model(ItemStack stack,ItemDisplayContext display,boolean left,PoseStack pose,
            MultiBufferSource buffers,int light,int overlay,BakedModel model,CallbackInfo ci) {
        ClientGliderVisualProbe.firstPersonModel(stack,display,left,model);
    }
}
