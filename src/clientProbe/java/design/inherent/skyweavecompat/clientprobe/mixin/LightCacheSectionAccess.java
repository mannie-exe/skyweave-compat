/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.clientprobe.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.DataLayerStorageMap;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LayerLightSectionStorage.class)
public interface LightCacheSectionAccess {
    @Accessor("visibleSectionData") DataLayerStorageMap<?> skyweave$visible();
    @Accessor("updatingSectionData") DataLayerStorageMap<?> skyweave$updating();
    @Accessor("queuedSections") Long2ObjectMap<DataLayer> skyweave$queued();
    @Accessor("chunkSource") LightChunkGetter skyweave$source();
    @Accessor("layer") LightLayer skyweave$layer();
    @Invoker("lightOnInSection") boolean skyweave$lightEnabled(long section);
}
