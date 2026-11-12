package design.inherent.skyweavecompat.terrain;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Natural skin candidates shared by surface provenance and shallow structural earthworks. */
public final class NaturalMaterials {
    private NaturalMaterials() {}
    /** Incidental generation decoration may be cleared only where an admitted plan originally had air. */
    public static boolean clearableDecoration(BlockState state) {
        return state.canBeReplaced() && !state.blocksMotion() && state.getFluidState().isEmpty()
                && !state.hasBlockEntity() && !state.getBlock().hasDynamicShape();
    }
    public static boolean skin(BlockState state) {
        return state.getFluidState().isEmpty() && !state.hasBlockEntity() && (state.is(BlockTags.DIRT)
                || state.is(BlockTags.SAND) || state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(BlockTags.TERRACOTTA) || state.is(Blocks.SANDSTONE)
                || state.is(Blocks.RED_SANDSTONE) || state.is(Blocks.GRAVEL)
                || state.is(Blocks.CALCITE) || state.is(Blocks.TUFF)
                || state.is(Blocks.MUD) || state.is(Blocks.CLAY)
                || state.is(Blocks.MOSS_BLOCK) || state.is(Blocks.SCULK) || state.is(Blocks.SNOW_BLOCK)
                || state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE));
    }
}
