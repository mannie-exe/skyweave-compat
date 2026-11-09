package design.inherent.skyweavecompat.mixin;

import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChunkMap.class)
public interface StructureSearchChunksAccess {
    @Invoker("getChunks") Iterable<ChunkHolder> skyweave$searchChunks();
    @Accessor("updatingChunkMap") Long2ObjectLinkedOpenHashMap<ChunkHolder> skyweave$searchUpdatingChunks();
    @Accessor("pendingUnloads") Long2ObjectLinkedOpenHashMap<ChunkHolder> skyweave$searchUnloadingChunks();
}
