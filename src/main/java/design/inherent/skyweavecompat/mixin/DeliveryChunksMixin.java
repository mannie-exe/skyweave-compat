package design.inherent.skyweavecompat.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import design.inherent.skyweavecompat.placement.DeliveryChunks;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import java.util.concurrent.CompletableFuture;

@Pseudo
@Mixin(targets = "com.ladderstar.adq.QuestGenerator", remap = false)
public abstract class DeliveryChunksMixin {
    @WrapOperation(method = "lambda$generateNewQuestAsync$9", require = 2, allow = 2,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerChunkCache;getChunkFuture(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Ljava/util/concurrent/CompletableFuture;"))
    private static CompletableFuture<ChunkResult<ChunkAccess>> skyweave$loadDeliveryChunks(ServerChunkCache cache,
            int x, int z, ChunkStatus status, boolean create, Operation<CompletableFuture<ChunkResult<ChunkAccess>>> original) {
        return DeliveryChunks.applies(cache, status, create) ? DeliveryChunks.request(cache, x, z)
                : original.call(cache, x, z, status, create);
    }

    @Inject(method = "registerGeneratedQuest", at = @At("RETURN"), require = 1)
    private static void skyweave$releaseSuccessfulRoute(CallbackInfo callback, @Local(argsOnly = true) ServerLevel level) {
        DeliveryChunks.finish(level);
    }

    @Inject(method = "failGeneration", at = @At("HEAD"), require = 1)
    private static void skyweave$releaseFailedRoute(CallbackInfo callback, @Local(argsOnly = true) ServerLevel level) {
        DeliveryChunks.finish(level);
    }

}
