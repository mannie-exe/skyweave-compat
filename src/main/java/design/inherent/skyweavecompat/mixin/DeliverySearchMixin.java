/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.mojang.datafixers.util.Pair;
import design.inherent.skyweavecompat.placement.DeliverySearch;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import net.minecraft.core.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import com.llamalad7.mixinextras.sugar.Local;

@Pseudo
@Mixin(targets="com.ladderstar.adq.QuestGenerator",remap=false)
public abstract class DeliverySearchMixin {
    @WrapOperation(method="generateNewQuestAsync(Lnet/minecraft/server/level/ServerLevel;Ljava/util/UUID;)V",at=@At(value="INVOKE",target="Ljava/util/concurrent/atomic/AtomicBoolean;compareAndSet(ZZ)Z"),require=1,allow=1)
    private static boolean entry(java.util.concurrent.atomic.AtomicBoolean flag,boolean expected,boolean next,Operation<Boolean> original) {
        boolean admitted=original.call(flag,expected,next);if(admitted)DeliverySearch.admittedNativeEntry();return admitted;
    }
    @WrapOperation(method="generateNewQuestAsync(Lnet/minecraft/server/level/ServerLevel;Ljava/util/UUID;)V",at=@At(value="INVOKE",target="Ljava/util/concurrent/CompletableFuture;runAsync(Ljava/lang/Runnable;)Ljava/util/concurrent/CompletableFuture;"),require=1,allow=1)
    private static CompletableFuture<Void> dispatch(Runnable action,Operation<CompletableFuture<Void>> original,@Local(argsOnly=true) ServerLevel level,@Local(argsOnly=true) UUID recipient) {
        return DeliverySearch.dispatches(level)?DeliverySearch.dispatch(level,recipient,action):original.call(action);
    }
    @WrapOperation(method="lambda$generateNewQuestAsync$9",at=@At(value="INVOKE",target="Ljava/util/concurrent/atomic/AtomicBoolean;set(Z)V"),require=11,allow=11)
    private static void producerFlag(java.util.concurrent.atomic.AtomicBoolean flag,boolean value,Operation<Void> original) {
        if(DeliverySearch.nativeReset(flag,value,true))original.call(flag,value);
    }
    @WrapOperation(method="finishGeneration",at=@At(value="INVOKE",target="Ljava/util/concurrent/atomic/AtomicBoolean;set(Z)V"),require=1,allow=1)
    private static void finalFlag(java.util.concurrent.atomic.AtomicBoolean flag,boolean value,Operation<Void> original) {
        if(DeliverySearch.nativeReset(flag,value,false))original.call(flag,value);
    }
    @WrapMethod(method="lambda$generateNewQuestAsync$9")
    private static void scope(ServerLevel level,UUID recipient,boolean custom,@Coerce Object template,List<?> templates,Operation<Void> original) {
        try(var scope=DeliverySearch.enter(level,recipient)){original.call(level,recipient,custom,template,templates);}
    }
    @WrapOperation(method="lambda$generateNewQuestAsync$9",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/chunk/ChunkGenerator;findNearestMapStructure(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/HolderSet;Lnet/minecraft/core/BlockPos;IZ)Lcom/mojang/datafixers/util/Pair;"),require=2,allow=2)
    private static Pair<BlockPos,Holder<Structure>> locate(ChunkGenerator generator,ServerLevel level,HolderSet<Structure> targets,BlockPos origin,int radius,boolean skip,Operation<Pair<BlockPos,Holder<Structure>>> original) {
        return DeliverySearch.locate(level,()->original.call(generator,level,targets,origin,radius,skip));
    }
    @WrapOperation(method="lambda$generateNewQuestAsync$9",at=@At(value="INVOKE",target="Ljava/util/concurrent/CompletableFuture;thenCombine(Ljava/util/concurrent/CompletionStage;Ljava/util/function/BiFunction;)Ljava/util/concurrent/CompletableFuture;"),require=1,allow=1)
    private static CompletableFuture<Object> finalization(CompletableFuture<ChunkResult<?>> left,CompletionStage<ChunkResult<?>> right,BiFunction<ChunkResult<?>,ChunkResult<?>,Object> continuation,Operation<CompletableFuture<Object>> original) {
        return original.call(left,right,DeliverySearch.continuation(continuation));
    }
    @WrapOperation(method="lambda$generateNewQuestAsync$9",at=@At(value="INVOKE",target="Ljava/util/concurrent/CompletableFuture;exceptionally(Ljava/util/function/Function;)Ljava/util/concurrent/CompletableFuture;"),require=1,allow=1)
    private static CompletableFuture<Object> failure(CompletableFuture<Object> future,Function<Throwable,Object> failure,Operation<CompletableFuture<Object>> original) {
        return original.call(future,DeliverySearch.failure(failure));
    }
    @WrapOperation(method={"lambda$generateNewQuestAsync$5","lambda$generateNewQuestAsync$7"},at=@At(value="INVOKE",target="Lnet/minecraft/server/MinecraftServer;execute(Ljava/lang/Runnable;)V"),require=2,allow=2)
    private static void publication(MinecraftServer server,Runnable action,Operation<Void> original) {
        original.call(server,DeliverySearch.publication(action));
    }
}
