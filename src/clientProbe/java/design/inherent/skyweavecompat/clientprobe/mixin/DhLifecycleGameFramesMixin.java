/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.clientprobe.mixin;

import design.inherent.skyweavecompat.clientprobe.ClientDhLifecycleProbe;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
abstract class DhLifecycleGameFramesMixin {
    @Inject(method="render",at=@At("HEAD"),require=1)
    private void frame(CallbackInfo ci) { ClientDhLifecycleProbe.nativeFrame("minecraftRenderEntries"); }
    @Inject(method="renderLevel",at=@At("HEAD"),require=1)
    private void world(CallbackInfo ci) { ClientDhLifecycleProbe.nativeFrame("minecraftWorldRenderEntries"); }
}
