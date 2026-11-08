package design.inherent.skyweavecompat.mixin;

import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerChunkCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Minimum-status search leases must not request FULL chunks through a region-radius ticket. */
@Mixin(ServerChunkCache.class)
public interface StructureSearchCacheAccess {
    @Accessor("distanceManager") DistanceManager skyweave$searchTickets();
}
