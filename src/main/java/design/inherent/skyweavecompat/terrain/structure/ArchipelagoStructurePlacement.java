package design.inherent.skyweavecompat.terrain.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;

/** Destination-local opportunities preserve native structure generation without requiring a distant void visit. */
public final class ArchipelagoStructurePlacement extends RandomSpreadStructurePlacement {
    private final RandomSpreadStructurePlacement original;
    private final StructureFamily family;
    private final StructureDistribution distribution;

    public ArchipelagoStructurePlacement(RandomSpreadStructurePlacement original, StructureFamily family,
            StructureDistribution distribution) {
        super(StructureDistribution.SAMPLE_SPACING, StructureDistribution.SAMPLE_SEPARATION,
                RandomSpreadType.LINEAR, StructureDistribution.SAMPLE_SALT);
        this.original = original; this.family = family; this.distribution = distribution;
    }

    public StructureFamily family() { return family; }
    public StructureDistribution distribution() { return distribution; }

    @Override protected boolean isPlacementChunk(ChunkGeneratorStructureState state, int x, int z) {
        return super.isPlacementChunk(state, x, z) && distribution.selected(family, x, z);
    }
    @Override public BlockPos getLocatePos(ChunkPos chunk) { return original.getLocatePos(chunk); }
    // Original frequency/exclusion are deliberately not applied a second time:
    // the owned distribution accounts for source density and final village anchors.
}
