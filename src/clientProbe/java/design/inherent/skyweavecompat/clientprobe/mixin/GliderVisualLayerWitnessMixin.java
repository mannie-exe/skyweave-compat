package design.inherent.skyweavecompat.clientprobe.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import design.inherent.skyweavecompat.clientprobe.ClientGliderVisualProbe;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

/** Observe the actual upstream-supplied operand without changing either provider's draw. */
@Mixin(ItemInHandLayer.class)
abstract class GliderVisualLayerWitnessMixin {
    @WrapMethod(method="renderArmWithItem")
    private void fixture$operand(LivingEntity entity, ItemStack stack, ItemDisplayContext display, HumanoidArm arm,
            PoseStack pose, MultiBufferSource buffers, int light, Operation<Void> original) {
        var model=((ItemInHandLayer<?,?>)(Object)this).getParentModel();
        var head=model instanceof net.minecraft.client.model.HumanoidModel<?> humanoid?humanoid.head:null;
        design.inherent.skyweavecompat.clientprobe.ClientGliderPresentationProbe.layer(entity,stack,pose,head,()->
                ClientGliderVisualProbe.layer(entity,stack,display,arm,
                        ()->original.call(entity,stack,display,arm,pose,buffers,light)));
    }
}
