/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import design.inherent.skyweavecompat.lighting.InitialClientLight;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientPacketListener.class)
public abstract class InitialLightPacketMixin {
    @Shadow private ClientLevel level;
    @WrapMethod(method="handleLevelChunkWithLight")
    private void skyweave$incoming(ClientboundLevelChunkWithLightPacket packet,Operation<Void> original) {
        InitialClientLight.incoming((ClientPacketListener)(Object)this,level,packet.getX(),packet.getZ(),()->original.call(packet));
    }
    @WrapOperation(method="handleLevelChunkWithLight",at=@At(value="INVOKE",target="Lnet/minecraft/client/multiplayer/ClientLevel;queueLightUpdate(Ljava/lang/Runnable;)V"),require=1,allow=1)
    private void skyweave$queued(ClientLevel owner,Runnable task,Operation<Void> original) {
        original.call(owner,InitialClientLight.queued(owner,task));
    }
}
