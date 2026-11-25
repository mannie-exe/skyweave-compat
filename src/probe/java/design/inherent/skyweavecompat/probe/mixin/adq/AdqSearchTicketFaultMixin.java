/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.probe.mixin.adq;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import design.inherent.skyweavecompat.placement.DeliverySearch;
import design.inherent.skyweavecompat.probe.AdqTicketFaultProbe;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(DeliverySearch.class)
public abstract class AdqSearchTicketFaultMixin {
    @WrapOperation(method="lambda$register$1",at=@At(value="INVOKE",target="Lnet/minecraft/server/level/DistanceManager;addTicket(Lnet/minecraft/server/level/TicketType;Lnet/minecraft/world/level/ChunkPos;ILjava/lang/Object;)V"),require=1,allow=1)
    private static void add(DistanceManager manager,TicketType<?> type,ChunkPos at,int level,Object key,Operation<Void> original) {
        original.call(manager,type,at,level,key);AdqTicketFaultProbe.search(manager,type,at,level);
    }
}
