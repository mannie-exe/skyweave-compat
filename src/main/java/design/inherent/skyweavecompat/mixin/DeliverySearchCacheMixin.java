/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import design.inherent.skyweavecompat.placement.DeliverySearch;
import java.util.concurrent.*;
import java.util.function.Supplier;
import net.minecraft.server.level.*;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerChunkCache.class)
public abstract class DeliverySearchCacheMixin {
    @WrapMethod(method="getChunk")
    private ChunkAccess chunk(int x,int z,ChunkStatus status,boolean create,Operation<ChunkAccess> original) {
        var cache=(ServerChunkCache)(Object)this;
        return DeliverySearch.intercept(cache)?DeliverySearch.chunk(cache,x,z,status,create):original.call(x,z,status,create);
    }
    @WrapOperation(method="getChunkFuture",at=@At(value="INVOKE",target="Ljava/util/concurrent/CompletableFuture;supplyAsync(Ljava/util/function/Supplier;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"),require=1,allow=1)
    private CompletableFuture<?> registration(Supplier<?> supplier,Executor executor,Operation<CompletableFuture<?>> original) {
        return original.call(DeliverySearch.registration(supplier),executor);
    }
}
