package design.inherent.skyweavecompat.terrain.structure;

import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;

public interface VillageDelegate {
    StructurePoolElement skyweave$delegate();
    default java.util.Optional<net.minecraft.world.level.levelgen.structure.TerrainAdjustment> skyweave$terrainAdjustment() {
        return java.util.Optional.empty();
    }
}
