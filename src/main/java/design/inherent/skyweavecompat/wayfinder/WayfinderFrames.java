package design.inherent.skyweavecompat.wayfinder;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;

/** Optional world-frame admission, without exposing foreign classes to the native path. */
public interface WayfinderFrames {
    boolean advance(int budget);
    double firstDistance();
    boolean storage(BlockPos position);
    boolean storage(ChunkPos position);

    static WayfinderFrames open(Level level, SightRay ray) {
        return net.neoforged.fml.ModList.get().isLoaded("sable")
                ? new design.inherent.skyweavecompat.wayfinder.sable.SableWayfinderFrames(level,ray) : null;
    }
}
