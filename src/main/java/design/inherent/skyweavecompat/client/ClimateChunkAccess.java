package design.inherent.skyweavecompat.client;

import design.inherent.skyweavecompat.climate.PackedClimateChunk;
import java.util.UUID;

/** Client-only unsaved data; native chunk unload releases it. */
public interface ClimateChunkAccess {
    record Data(UUID epoch,PackedClimateChunk chunk) {}
    Data skyweave$climate();
    void skyweave$climate(Data data);
}
