package design.inherent.skyweavecompat.probe.mixin;

import design.inherent.skyweavecompat.probe.HydrologyProbe;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Bounded test-only attribution after a real chunk write, never a proposed write receipt. */
@Mixin({ProtoChunk.class,LevelChunk.class})
abstract class WaterWriteMixin {
    private static final boolean WATER=!System.getProperty("neighbors.probe.waterWitnesses","").isEmpty();
    @Inject(method="setBlockState",at=@At("RETURN"))
    private void observe(BlockPos position,BlockState state,boolean moved,CallbackInfoReturnable<BlockState> callback) {
        if(WATER && callback.getReturnValue()!=null)
            HydrologyProbe.recordTerrainWrite((ChunkAccess)(Object)this,position,callback.getReturnValue());
    }
}
