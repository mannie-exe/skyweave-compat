package design.inherent.skyweavecompat.mixin;

import com.mojang.datafixers.util.Either;
import design.inherent.skyweavecompat.terrain.structure.StructureSearchStorage;
import design.inherent.skyweavecompat.terrain.structure.StructureStorageInventory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.IOWorker;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Inventory is serialized with native reads, pending writes and region-cache eviction. */
@Mixin(IOWorker.class)
public abstract class StructureSearchStorageMixin implements StructureSearchStorage {
    @Shadow @Final private RegionFileStorage storage;
    @Shadow @Final private Map<ChunkPos, ?> pendingWrites;
    @Shadow private <T> CompletableFuture<T> submitTask(Supplier<Either<T, Exception>> task) { throw new AssertionError(); }

    @Override public CompletableFuture<Regions> skyweave$regions(int minX, int minZ, int maxX, int maxZ, int limit) {
        // An unsupported replacement must qualify coverage, never claim a saved-world absence.
        if (storage == null) return CompletableFuture.completedFuture(new Regions(List.of(), true));
        return submitTask(() -> {
            try { return Either.left(StructureStorageInventory.regions(storage, pendingWrites.keySet(), minX, minZ, maxX, maxZ, limit)); }
            catch (Exception failure) { return Either.right(failure); }
        });
    }
    @Override public CompletableFuture<List<ChunkPos>> skyweave$storedChunks(ChunkPos region) {
        return submitTask(() -> {
            try { return Either.left(StructureStorageInventory.chunks(storage, pendingWrites.keySet(), region)); }
            catch (Exception failure) { return Either.right(failure); }
        });
    }
}
