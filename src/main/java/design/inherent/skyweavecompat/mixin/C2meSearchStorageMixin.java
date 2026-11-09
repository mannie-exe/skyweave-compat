package design.inherent.skyweavecompat.mixin;

import design.inherent.skyweavecompat.terrain.structure.StructureSearchStorage;
import design.inherent.skyweavecompat.terrain.structure.StructureStorageInventory;
import it.unimi.dsi.fastutil.longs.Long2ReferenceLinkedOpenHashMap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Uses C2ME's serial storage executor, including cached and not-yet-written positions. */
@Mixin(targets = "com.ishland.c2me.rewrites.chunkio.common.C2MEStorageThread", remap = false)
public abstract class C2meSearchStorageMixin implements StructureSearchStorage {
    @Shadow @Final private RegionFileStorage storage;
    @Shadow @Final private Executor executor;
    @Shadow @Final private Long2ReferenceLinkedOpenHashMap<?> cache;
    @Shadow @Final private Long2ReferenceLinkedOpenHashMap<?> writeBacklog;
    @Unique private Iterable<ChunkPos> skyweave$pending() {
        return () -> java.util.stream.LongStream.concat(cache.keySet().longStream(), writeBacklog.keySet().longStream())
                .mapToObj(ChunkPos::new).iterator();
    }
    @Override public CompletableFuture<Regions> skyweave$regions(int minX, int minZ, int maxX, int maxZ, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            try { return StructureStorageInventory.regions(storage, skyweave$pending(), minX, minZ, maxX, maxZ, limit); }
            catch (java.io.IOException failure) { throw new UncheckedIOException(failure); }
        }, executor);
    }
    @Override public CompletableFuture<List<ChunkPos>> skyweave$storedChunks(ChunkPos region) {
        return CompletableFuture.supplyAsync(() -> {
            try { return StructureStorageInventory.chunks(storage, skyweave$pending(), region); }
            catch (java.io.IOException failure) { throw new UncheckedIOException(failure); }
        }, executor);
    }
}
