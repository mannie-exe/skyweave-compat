package design.inherent.skyweavecompat.mixin.client;

import design.inherent.skyweavecompat.client.ClimateChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LevelChunk.class)
public abstract class ChunkClimateMixin implements ClimateChunkAccess {
    @Unique private Data skyweave$climate;
    @Override public Data skyweave$climate() { return skyweave$climate; }
    @Override public void skyweave$climate(Data data) { skyweave$climate=data; }
}
