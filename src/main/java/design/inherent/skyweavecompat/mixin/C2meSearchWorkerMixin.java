package design.inherent.skyweavecompat.mixin;

import com.ishland.c2me.rewrites.chunkio.common.C2MEStorageThread;
import design.inherent.skyweavecompat.terrain.structure.StructureSearchStorage;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** C2ME clears the inherited IOWorker fields; use its actual queue/storage owner. */
@Mixin(targets = "com.ishland.c2me.rewrites.chunkio.common.C2MEStorageVanillaInterface", remap = false)
public abstract class C2meSearchWorkerMixin implements StructureSearchStorage {
    @Shadow @Final private C2MEStorageThread backend;
    @Override public CompletableFuture<Regions> skyweave$regions(int minX, int minZ, int maxX, int maxZ, int limit) {
        return ((StructureSearchStorage)(Object)backend).skyweave$regions(minX, minZ, maxX, maxZ, limit);
    }
    @Override public CompletableFuture<List<ChunkPos>> skyweave$storedChunks(ChunkPos region) {
        return ((StructureSearchStorage)(Object)backend).skyweave$storedChunks(region);
    }
}
