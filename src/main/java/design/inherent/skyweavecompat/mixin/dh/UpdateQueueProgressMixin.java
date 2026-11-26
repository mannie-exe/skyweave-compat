package design.inherent.skyweavecompat.mixin.dh;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.seibel.distanthorizons.core.api.internal.chunkUpdating.ChunkPosQueue;
import com.seibel.distanthorizons.core.api.internal.chunkUpdating.ChunkUpdateData;
import com.seibel.distanthorizons.core.api.internal.chunkUpdating.ChunkUpdateQueueManager;
import com.seibel.distanthorizons.core.pos.DhChunkPos;
import design.inherent.skyweavecompat.dh.UpdateQueueWait;
import java.util.concurrent.AbstractExecutorService;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Park only DH's own worker after a non-progressing native pass. */
@Mixin(value = ChunkUpdateQueueManager.class, remap = false)
abstract class UpdateQueueProgressMixin {
    @Shadow @Final public ChunkPosQueue updateQueue;
    @Shadow @Final public ChunkPosQueue preUpdateQueue;
    @Shadow @Final public static long UPDATE_DEBOUNCE_NANOS;
    @Unique private volatile UpdateQueueWait skyweave$retryWait;

    @Unique private UpdateQueueWait skyweave$wait() {
        // DH starts its worker inside the constructor, before Mixin field initializers may run.
        UpdateQueueWait value = skyweave$retryWait;
        if (value == null) synchronized (this) {
            value = skyweave$retryWait;
            if (value == null) skyweave$retryWait = value = new UpdateQueueWait();
        }
        return value;
    }

    @WrapOperation(method = "runQueueingLoop", at = @At(value = "INVOKE",
            target = "Lcom/seibel/distanthorizons/core/api/internal/chunkUpdating/ChunkUpdateQueueManager;processQueue()V"), require = 1)
    private void skyweave$progress(ChunkUpdateQueueManager manager, Operation<Void> original) {
        UpdateQueueWait wait = skyweave$wait();
        wait.beginPass();
        original.call(manager);
        if (preUpdateQueue.isEmpty() && !updateQueue.isEmpty()) wait.awaitRetry();
    }

    @WrapOperation(method = "tryDispatchReadyChunkUpdate", at = @At(value = "INVOKE",
            target = "Lcom/seibel/distanthorizons/core/api/internal/chunkUpdating/ChunkPosQueue;addItem(Lcom/seibel/distanthorizons/core/pos/DhChunkPos;Lcom/seibel/distanthorizons/core/api/internal/chunkUpdating/ChunkUpdateData;)V"), require = 3, expect = 3)
    private void skyweave$retry(ChunkPosQueue queue, DhChunkPos pos, ChunkUpdateData data, Operation<Void> original) {
        original.call(queue, pos, data);
        skyweave$wait().retried(System.nanoTime() - data.firstQueuedNanoTime, UPDATE_DEBOUNCE_NANOS);
    }

    @WrapOperation(method = "tryDispatchReadyChunkUpdate", at = @At(value = "INVOKE",
            target = "Ljava/util/concurrent/AbstractExecutorService;execute(Ljava/lang/Runnable;)V"), require = 1)
    private void skyweave$dispatch(AbstractExecutorService executor, Runnable task, Operation<Void> original) {
        original.call(executor, task);
        skyweave$wait().dispatched();
    }

    @Inject(method = {"addItemToQueue", "setCenter", "clear"}, at = @At("RETURN"))
    private void skyweave$wake(CallbackInfo ci) { skyweave$wait().signal(); }

    @Inject(method = "close", at = @At("HEAD"))
    private void skyweave$close(CallbackInfo ci) { skyweave$wait().close(); }
}
