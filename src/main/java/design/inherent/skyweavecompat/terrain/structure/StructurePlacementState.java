package design.inherent.skyweavecompat.terrain.structure;

import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;

/** Runtime state is owned by one world generator, not a process-wide placement registry. */
public interface StructurePlacementState {
    void skyweave$configureStructures(SkyIslandChunkGenerator generator);
    StructureDistribution skyweave$structureDistribution();
}
