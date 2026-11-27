package design.inherent.skyweavecompat.clientprobe.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Passive native encoder operands; preserve the original writer and fast path. */
@Mixin(targets="net.caffeinemc.mods.sodium.client.render.immediate.model.BakedModelEncoder",remap=false)
abstract class GliderVisualSodiumQuadWitnessMixin {
    @Inject(method="writeQuadVertices(Lnet/caffeinemc/mods/sodium/api/vertex/buffer/VertexBufferWriter;Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lnet/caffeinemc/mods/sodium/client/model/quad/ModelQuadView;IIIZ)V",at=@At("HEAD"),require=1,allow=1)
    private static void fixture$quad(@Coerce Object writer,PoseStack.Pose pose,@Coerce Object quad,
            int color,int light,int overlay,boolean multiplyAlpha,CallbackInfo ci) {
        design.inherent.skyweavecompat.clientprobe.ClientGliderPresentationProbe.sodiumQuad(pose,quad,color);
    }
}
