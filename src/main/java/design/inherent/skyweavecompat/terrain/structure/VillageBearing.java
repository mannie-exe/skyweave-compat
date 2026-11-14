package design.inherent.skyweavecompat.terrain.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

/** Authored bearing intent; contextual stability and actual contact remain separate checks. */
final class VillageBearing {
    enum Kind {
        NONE, STATIC, SCAFFOLD, WET, UNKNOWN;
        boolean bearing() { return this==STATIC || this==SCAFFOLD; }
        boolean unavailable() { return this==WET || this==UNKNOWN; }
    }
    private VillageBearing() {}

    private static boolean excluded(BlockState state) {
        return state.is(BlockTags.LEAVES) || state.is(Blocks.JIGSAW)
                || state.is(Blocks.STRUCTURE_BLOCK) || state.is(Blocks.STRUCTURE_VOID);
    }
    static boolean root(BlockState state) {
        return (state.blocksMotion() || state.is(Blocks.SCAFFOLDING))
                && !excluded(state);
    }

    static Kind classify(BlockState state) {
        if(excluded(state))return Kind.NONE;
        if(!state.getFluidState().isEmpty())return Kind.WET;
        // Native scaffolding is non-motion-blocking and dynamic. This admits its
        // floor intent, never a context-free collision or native stability claim.
        if(state.is(Blocks.SCAFFOLDING))return Kind.SCAFFOLD;
        if(state.getBlock().hasDynamicShape())return Kind.UNKNOWN;
        // Non-dynamic states have native cached support faces. Block-entity data
        // is neither read nor modified by this geometric decision.
        if(state.isFaceSturdy(EmptyBlockGetter.INSTANCE,BlockPos.ZERO,Direction.DOWN))return Kind.STATIC;
        if(state.getBlock() instanceof FenceBlock || state.getBlock() instanceof WallBlock
                || state.getBlock() instanceof StairBlock) {
            var shape=state.getBlockSupportShape(EmptyBlockGetter.INSTANCE,BlockPos.ZERO);
            if(!shape.isEmpty() && shape.min(Direction.Axis.Y)==0)return Kind.STATIC;
        }
        return state.hasBlockEntity()?Kind.UNKNOWN:Kind.NONE;
    }
}
