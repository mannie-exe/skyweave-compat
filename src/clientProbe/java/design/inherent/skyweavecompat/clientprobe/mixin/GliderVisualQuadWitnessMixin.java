package design.inherent.skyweavecompat.clientprobe.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.entity.ItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Observe actual native bulk submissions, forwarding every original operand once. */
@Mixin(ItemRenderer.class)
abstract class GliderVisualQuadWitnessMixin {
    @WrapOperation(method="renderQuadList",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/vertex/VertexConsumer;putBulkData(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lnet/minecraft/client/renderer/block/model/BakedQuad;FFFFIIZ)V"),require=1,allow=1)
    private void fixture$quad(VertexConsumer target,PoseStack.Pose pose,BakedQuad quad,float r,float g,float b,float a,
            int light,int overlay,boolean quadColor,Operation<Void> original) {
        design.inherent.skyweavecompat.clientprobe.ClientGliderPresentationProbe.quad(pose,quad,r,g,b,a);
        original.call(target,pose,quad,r,g,b,a,light,overlay,quadColor);
    }
}
