package design.inherent.skyweavecompat.terrain.structure;

import java.util.EnumSet;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;

/** Rebuild rather than re-prime a previously populated map, including idempotent terrain replay. */
final class StructureHeights {
    private StructureHeights() {}
    static void refresh(ChunkAccess chunk) {
        var types=EnumSet.of(Heightmap.Types.WORLD_SURFACE_WG,Heightmap.Types.OCEAN_FLOOR_WG);
        for(var entry:chunk.getHeightmaps())types.add(entry.getKey());
        for(var type:types) {
            var map=chunk.getOrCreateHeightmapUnprimed(type);map.setRawData(chunk,type,new long[map.getRawData().length]);
        }
        Heightmap.primeHeightmaps(chunk,types);
    }
}
