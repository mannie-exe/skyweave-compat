package design.inherent.skyweavecompat.probe.mixin;

import design.inherent.skyweavecompat.probe.HydrologyProbe;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ProtoChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Attribute snow/ice writes at explicitly configured native generation witnesses. */
@Mixin(ProtoChunk.class)
abstract class ClimateWriteMixin {
    @Inject(method = "setBlockState", at = @At("HEAD"))
    private void observe(BlockPos pos, BlockState state, boolean moved, CallbackInfoReturnable<BlockState> callback) {
        if (state.is(Blocks.ICE) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.SNOW))
            HydrologyProbe.recordClimateWrite(pos, state);
    }
}
