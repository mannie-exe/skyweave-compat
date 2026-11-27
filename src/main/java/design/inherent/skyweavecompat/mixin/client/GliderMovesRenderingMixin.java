/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.mixin.client;

import com.evandev.reliable_gliders.api.GlidingState;
import com.evandev.reliable_gliders.registry.ModItems;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import punchymoves.client.compat.SpatialGuiCompat;

/** Preserve the equipped glider operand already supplied by Reliable Gliders' native layer. */
@Mixin(value=ItemInHandLayer.class,priority=1500)
abstract class GliderMovesRenderingMixin {
    @Inject(method="@skyweave:glidermoves(item)",at=@At("HEAD"),cancellable=true,require=1,allow=1,remap=false)
    private void skyweave$equippedGlider(ItemInHandRenderer renderer,LivingEntity entity,ItemStack stack,
            ItemDisplayContext display,boolean left,PoseStack pose,MultiBufferSource buffers,int light,
            Operation<Void> nativeDraw,CallbackInfo ci) {
        if(!(entity instanceof AbstractClientPlayer player) || !stack.is(ModItems.GLIDER) || !GlidingState.isGliding(player)
                || player.getMainHandItem().is(ModItems.GLIDER) || player.getOffhandItem().is(ModItems.GLIDER)
                || SpatialGuiCompat.hidesEquippedItems(player)
                || SpatialGuiCompat.ownsCamera() && SpatialGuiCompat.posesAvatar(player))return;
        // Moves' item animation and delayed inventory substitute belong to real held items.
        // This is the upstream equipped attachment, with its original components and pose.
        nativeDraw.call(renderer,entity,stack,display,left,pose,buffers,light);
        ci.cancel();
    }
}
