package design.inherent.skyweavecompat.probe.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import design.inherent.skyweavecompat.probe.HydrologyProbe;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatusTasks;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Observe completed native stages before their returned future advances the pipeline. */
@Mixin(ChunkStatusTasks.class)
abstract class WaterStageMixin {
    @ModifyReturnValue(method = {"generateNoise", "generateSurface", "generateCarvers", "generateFeatures"}, at = @At("RETURN"))
    private static CompletableFuture<ChunkAccess> observe(CompletableFuture<ChunkAccess> future,
            WorldGenContext context, ChunkStep step, StaticCache2D<GenerationChunkHolder> cache, ChunkAccess input) {
        if (System.getProperty("neighbors.probe.waterWitnesses", "").isEmpty()
                || context.level().dimension() != net.minecraft.world.level.Level.OVERWORLD) return future;
        return future.thenApply(chunk -> {
            HydrologyProbe.recordStage(context.level().getSeed(), step.targetStatus(), chunk);
            return chunk;
        });
    }
}
