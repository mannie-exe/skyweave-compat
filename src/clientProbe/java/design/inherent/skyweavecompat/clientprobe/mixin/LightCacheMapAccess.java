/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.clientprobe.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.lighting.DataLayerStorageMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read a map without changing its native two-entry lookup cache. */
@Mixin(DataLayerStorageMap.class)
public interface LightCacheMapAccess {
    @Accessor("map") Long2ObjectOpenHashMap<DataLayer> skyweave$map();
}
