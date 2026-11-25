/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.probe.mixin.curio;

import design.inherent.skyweavecompat.probe.CurioBreakProbe;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "io.wispforest.accessories.networking.AccessoriesNetworking", remap = false)
abstract class AccessoryBreakPacketProbeMixin {
    @Inject(method = "sendToTrackingAndSelf(Lnet/minecraft/world/entity/Entity;Ljava/lang/Record;)V", at = @At("HEAD"), require = 1, allow = 1)
    private static void count(Entity entity, Record payload, CallbackInfo callback) { CurioBreakProbe.accessory(payload); }
}
