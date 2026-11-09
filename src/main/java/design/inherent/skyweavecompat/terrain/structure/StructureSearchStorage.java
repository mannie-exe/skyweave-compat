package design.inherent.skyweavecompat.terrain.structure;

import net.minecraft.world.level.ChunkPos;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Storage inventory on the owning native IO mailbox; never reads region files independently. */
public interface StructureSearchStorage {
    record Regions(List<ChunkPos> positions, boolean limited) {
        public Regions { positions = List.copyOf(positions); }
    }
    CompletableFuture<Regions> skyweave$regions(int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ, int limit);
    CompletableFuture<List<ChunkPos>> skyweave$storedChunks(ChunkPos region);
}
