/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.probe.mixin.adq;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import design.inherent.skyweavecompat.placement.DeliverySearch;
import design.inherent.skyweavecompat.probe.AdqSearchProbe;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;

/** Observes the actual replacement ingress; the sync method's skipped body cannot witness it. */
@Mixin(DeliverySearch.class)
public abstract class AdqBridgeWitnessMixin {
    @WrapMethod(method="chunk")
    private static ChunkAccess read(ServerChunkCache cache,int x,int z,ChunkStatus status,boolean create,Operation<ChunkAccess> original) {
        var prior=AdqSearchProbe.REQUEST.get();
        if(prior!=null)return original.call(cache,x,z,status,create);
        var row=AdqSearchProbe.beginBridge(x,z,status.toString(),create);
        row.addProperty("actualOwnedIntercept",DeliverySearch.intercept(cache));
        if(!DeliverySearch.intercept(cache) || DeliverySearch.token()==null)throw new AssertionError("Bridge observer lacks the actual owned locator");
        AdqSearchProbe.REQUEST.set(row);
        try {
            var result=original.call(cache,x,z,status,create);row.addProperty("returnedChunkPresent",result!=null);
            if(result!=null){row.addProperty("returnedChunkPosition",result.getPos().toLong());row.addProperty("returnedChunkStatus",result.getPersistedStatus().toString());}
            return result;
        }catch(RuntimeException error){row.addProperty("actualReadFailure",error.toString());throw error;}
        finally{AdqSearchProbe.REQUEST.remove();AdqSearchProbe.end(row);}
    }
}
