/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.mixin.client;

import com.evandev.reliable_gliders.api.GlidingState;
import com.evandev.reliable_gliders.registry.ModItems;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Retain the native glider model alongside Punchy's independently rendered actual hands. */
@Mixin(value=ItemInHandRenderer.class,priority=1500)
abstract class GliderPunchyRenderingMixin {
    @WrapWithCondition(method="@skyweave:gliderpunchy(cancel)",at=@At(value="INVOKE",
            target="Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;cancel()V",ordinal=2),
            require=1,allow=1,remap=false)
    private boolean skyweave$nativeEquippedGlider(CallbackInfo nativeHand,
            @Local(argsOnly=true) AbstractClientPlayer player,@Local(argsOnly=true) ItemStack stack) {
        // Earlier native interface, blacklist, equipment and action branches still execute.
        // Skip only the final ordinary hand suppression for the supplied glider attachment.
        return player!=Minecraft.getInstance().player || !stack.is(ModItems.GLIDER) || !GlidingState.isGliding(player)
                || player.getMainHandItem().is(ModItems.GLIDER) || player.getOffhandItem().is(ModItems.GLIDER);
    }
}
