package design.inherent.skyweavecompat.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import java.io.IOException;
import java.nio.file.Path;

/** Accessed only inside the RegionFileStorage owner's IO mailbox. */
@Mixin(RegionFileStorage.class)
public interface StructureRegionStorageAccess {
    @Accessor("folder") Path skyweave$regionFolder();
    @Accessor("regionCache") Long2ObjectLinkedOpenHashMap<RegionFile> skyweave$regionCache();
    @Invoker("getRegionFile") RegionFile skyweave$region(ChunkPos position) throws IOException;
}
