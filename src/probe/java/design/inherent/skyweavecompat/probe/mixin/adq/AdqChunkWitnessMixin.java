/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.probe.mixin.adq;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import design.inherent.skyweavecompat.probe.AdqSearchProbe;
import design.inherent.skyweavecompat.probe.AdqLifetimeProbe;
import java.util.concurrent.*;
import java.util.function.Supplier;
import net.minecraft.server.level.*;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

@Mixin(value=ServerChunkCache.class,priority=500)
public abstract class AdqChunkWitnessMixin {
    @WrapMethod(method="getChunk")
    private ChunkAccess read(int x,int z,ChunkStatus status,boolean create,Operation<ChunkAccess> original) {
        var prior=AdqSearchProbe.REQUEST.get();var row=AdqSearchProbe.begin(x,z,status.toString(),create);
        if(row!=null)AdqSearchProbe.REQUEST.set(row);
        try{return original.call(x,z,status,create);}
        finally {
            if(prior==null)AdqSearchProbe.REQUEST.remove();else AdqSearchProbe.REQUEST.set(prior);
            AdqSearchProbe.end(row);
        }
    }
    @WrapOperation(method="getChunk",at=@At(value="INVOKE",target="Ljava/util/concurrent/CompletableFuture;supplyAsync(Ljava/util/function/Supplier;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"),require=1,allow=1)
    private CompletableFuture<ChunkAccess> dispatch(Supplier<ChunkAccess> supplied,Executor executor,Operation<CompletableFuture<ChunkAccess>> original) {
        var row=AdqSearchProbe.REQUEST.get();
        var future=original.call(row==null?supplied:AdqSearchProbe.carry(supplied,row),executor);
        AdqSearchProbe.future("serverTask",future);return future;
    }
    @WrapOperation(method="getChunkFuture",at=@At(value="INVOKE",target="Ljava/util/concurrent/CompletableFuture;supplyAsync(Ljava/util/function/Supplier;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"),require=1,allow=1)
    private CompletableFuture<?> registration(Supplier<?> supplied,Executor executor,Operation<CompletableFuture<?>> original) {
        var row=AdqSearchProbe.REQUEST.get();
        var future=original.call(row==null?supplied:AdqSearchProbe.carry(supplied,row),executor);
        AdqSearchProbe.future("registrationTask",future);return future;
    }
    @ModifyReturnValue(method="getChunkFutureMainThread",at=@At("RETURN"))
    private CompletableFuture<ChunkResult<ChunkAccess>> future(CompletableFuture<ChunkResult<ChunkAccess>> original) {
        AdqSearchProbe.future("nativeStatus",original);return AdqLifetimeProbe.gate(original);
    }
    @WrapOperation(method="getChunk",at=@At(value="INVOKE",target="Lnet/minecraft/server/level/ServerChunkCache$MainThreadExecutor;managedBlock(Ljava/util/function/BooleanSupplier;)V"),require=1,allow=1)
    private void waiting(@Coerce Object executor,java.util.function.BooleanSupplier complete,Operation<Void> original) {
        AdqSearchProbe.waitBoundary(false);
        try{original.call(executor,complete);}finally{AdqSearchProbe.waitBoundary(true);}
    }
}
