package design.inherent.skyweavecompat.probe.mixin;

import design.inherent.skyweavecompat.probe.GroundVillageProbe;
import design.inherent.skyweavecompat.probe.VillageTerrainTrace;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Exact-witness tracing only; no shipping hook or mutation. */
@Mixin({ProtoChunk.class,LevelChunk.class})
abstract class GroundWriteMixin {
    private static final boolean GROUND=Boolean.getBoolean("neighbors.probe.groundVillage");
    private static final boolean WITNESS=!System.getProperty("neighbors.probe.villageWitness","").isEmpty();
    @Inject(method="setBlockState",at=@At("HEAD"))
    private void observe(BlockPos position,BlockState state,boolean moved,CallbackInfoReturnable<BlockState> callback) {
        if(GROUND)GroundVillageProbe.recordWrite(position,state);
        if(WITNESS)VillageTerrainTrace.write((net.minecraft.world.level.chunk.ChunkAccess)(Object)this,position,state);
    }
}
