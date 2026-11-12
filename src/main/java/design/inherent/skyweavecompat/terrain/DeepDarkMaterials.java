package design.inherent.skyweavecompat.terrain;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Natural material policy for the selected contributor, never a late block replacement. */
public final class DeepDarkMaterials {
    private DeepDarkMaterials() {}

    public static boolean special(IslandStyles styles, IslandContext.Body owner) {
        return styles != null && owner != null && styles.style(owner.island()).kind() == IslandStyles.Kind.DEEP_DARK;
    }

    /** Retain the existing ore draw and filler; only choose the corresponding deep substrate. */
    public static BlockState body(BlockState state, boolean special) {
        if (!special) return state;
        if (state.is(Blocks.STONE)) return Blocks.DEEPSLATE.defaultBlockState();
        if (state.is(Blocks.COAL_ORE)) return Blocks.DEEPSLATE_COAL_ORE.defaultBlockState();
        if (state.is(Blocks.COPPER_ORE)) return Blocks.DEEPSLATE_COPPER_ORE.defaultBlockState();
        if (state.is(Blocks.IRON_ORE)) return Blocks.DEEPSLATE_IRON_ORE.defaultBlockState();
        if (state.is(Blocks.GOLD_ORE)) return Blocks.DEEPSLATE_GOLD_ORE.defaultBlockState();
        if (state.is(Blocks.REDSTONE_ORE)) return Blocks.DEEPSLATE_REDSTONE_ORE.defaultBlockState();
        if (state.is(Blocks.LAPIS_ORE)) return Blocks.DEEPSLATE_LAPIS_ORE.defaultBlockState();
        if (state.is(Blocks.DIAMOND_ORE)) return Blocks.DEEPSLATE_DIAMOND_ORE.defaultBlockState();
        if (state.is(Blocks.EMERALD_ORE)) return Blocks.DEEPSLATE_EMERALD_ORE.defaultBlockState();
        return state;
    }

    /** A coherent mask with small exposed-rock interruptions; coverage is a bias, not a measured quota. */
    public static boolean sculk(long seed, int x, int z, ShapeProfile.DeepDark profile) {
        if (profile.sculkCoverage() == 0) return false;
        if (profile.sculkCoverage() == 1) return true;
        double broad = IslandStyles.noise(seed ^ 0x436fb21L, x / (double)profile.surfaceScale(), z / (double)profile.surfaceScale());
        double detail = IslandStyles.noise(seed ^ 0x94b9e3L, x / 9.0, z / 9.0);
        return broad * .8 + detail * .2 < profile.sculkCoverage() * 2 - 1;
    }

    public static BlockState surface(BlockState selected, IslandStyles styles, IslandContext.Body owner,
            int x, int z, int depth) {
        if (!special(styles, owner) || depth < 0 || !NaturalMaterials.skin(selected)) return selected;
        return depth == 0 && sculk(owner.island().seed(), x, z, styles.profile().deepDark())
                ? Blocks.SCULK.defaultBlockState() : Blocks.DEEPSLATE.defaultBlockState();
    }
}
