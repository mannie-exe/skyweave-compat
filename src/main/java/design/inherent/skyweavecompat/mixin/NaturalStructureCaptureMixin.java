package design.inherent.skyweavecompat.mixin;

import design.inherent.skyweavecompat.terrain.structure.NaturalStructureInputs;
import design.inherent.skyweavecompat.terrain.structure.GroundSupportProofs;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native task boundary is outside the optional/cancellable generator carving implementation. */
@Mixin(ChunkStatusTasks.class)
public abstract class NaturalStructureCaptureMixin {
    @Inject(method="generateCarvers",at=@At("RETURN"))
    private static void skyweave$capture(WorldGenContext context,ChunkStep step,StaticCache2D<GenerationChunkHolder> cache,
            ChunkAccess chunk,CallbackInfoReturnable<CompletableFuture<ChunkAccess>> callback) {
        NaturalStructureInputs.capture(context.level(),chunk);
    }
    @Inject(method="light",at=@At("HEAD"))
    private static void skyweave$retire(WorldGenContext context,ChunkStep step,StaticCache2D<GenerationChunkHolder> cache,
            ChunkAccess chunk,CallbackInfoReturnable<CompletableFuture<ChunkAccess>> callback) {
        GroundSupportProofs.verify(context.level(),chunk);
        NaturalStructureInputs.retire(context.level(),chunk);
    }
}
