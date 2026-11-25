/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.probe.mixin.adq;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import design.inherent.skyweavecompat.probe.AdqTicketFaultProbe;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets="design.inherent.skyweavecompat.placement.DeliveryChunks$Lease")
public abstract class AdqEndpointTicketFaultMixin {
    @WrapOperation(method="acquire",at=@At(value="INVOKE",target="Lnet/minecraft/server/level/ServerChunkCache;addRegionTicket(Lnet/minecraft/server/level/TicketType;Lnet/minecraft/world/level/ChunkPos;ILjava/lang/Object;)V"),require=1,allow=1)
    private void add(ServerChunkCache cache,TicketType<?> type,ChunkPos at,int radius,Object key,Operation<Void> original) {
        original.call(cache,type,at,radius,key);AdqTicketFaultProbe.endpoint(this,cache,type,at,radius);
    }
}
