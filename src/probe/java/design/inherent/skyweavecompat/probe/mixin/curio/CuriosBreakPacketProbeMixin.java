/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.probe.mixin.curio;

import design.inherent.skyweavecompat.probe.CurioBreakProbe;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "top.theillusivec4.curios.mixin.CuriosImplMixinHooks", remap = false)
abstract class CuriosBreakPacketProbeMixin {
    @WrapOperation(method = "broadcastCurioBreakEvent", at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/network/PacketDistributor;sendToPlayersTrackingEntityAndSelf(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;[Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)V"), require = 1, allow = 1)
    private static void count(Entity entity, CustomPacketPayload payload, CustomPacketPayload[] rest, Operation<Void> original) {
        CurioBreakProbe.curiosPackets++; original.call(entity, payload, rest);
    }
}
