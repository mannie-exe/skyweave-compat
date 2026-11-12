package design.inherent.skyweavecompat.terrain;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.feature.Feature;
import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;

/** Natural feature/spawn policy; template-authored features and player activity remain native. */
public final class DeepDarkHabitat {
    private static final ThreadLocal<Integer> AUTHORED = new ThreadLocal<>();
    private static final ThreadLocal<Integer> NATURAL_SCULK = new ThreadLocal<>();
    private DeepDarkHabitat() {}

    public static void authored(Runnable action) {
        var previous = AUTHORED.get();
        AUTHORED.set(previous == null ? 1 : previous + 1);
        try { action.run(); }
        finally { if (previous == null) AUTHORED.remove(); else AUTHORED.set(previous); }
    }
    public static boolean authored() { return AUTHORED.get() != null; }

    public static boolean spreading(net.minecraft.world.level.levelgen.feature.ConfiguredFeature<?,?> feature) {
        return feature.feature()==Feature.SCULK_PATCH
                || feature.config() instanceof net.minecraft.world.level.levelgen.feature.configurations.MultifaceGrowthConfiguration growth
                    && growth.placeBlock==net.minecraft.world.level.block.Blocks.SCULK_VEIN;
    }
    public static boolean naturalSculk(java.util.function.BooleanSupplier action) {
        var previous=NATURAL_SCULK.get();NATURAL_SCULK.set(previous==null?1:previous+1);
        try { return action.getAsBoolean(); }
        finally { if(previous==null)NATURAL_SCULK.remove();else NATURAL_SCULK.set(previous); }
    }
    public static boolean protectsSculk(LevelAccessor level,BlockPos position) {
        return NATURAL_SCULK.get()!=null && !authored() && contains(level,position);
    }

    /** Intrinsic, read-only contributor lookup: no completed river plan or chunk request. */
    public static boolean contains(LevelAccessor access, BlockPos position) {
        if (!(access instanceof ServerLevelAccessor server)
                || !(server.getLevel().getChunkSource().getGenerator() instanceof SkyIslandChunkGenerator generator)
                || !((GeologicalGenerator)(Object)generator).skyweave$isEnabled()) return false;
        var field = ((GeologicalGenerator)(Object)generator).skyweave$field(server.getLevel().getChunkSource().randomState());
        var context = ((InteriorField)(Object)field).skyweave$contextAt(position.getX(),position.getZ(),generator.resolvedSkyIslandSettings());
        var owner = context.biomeOwner(position.getY());
        if (owner == null) owner = context.owner(position.getY(),4);
        return DeepDarkMaterials.special(((StyledTerrain)(Object)field).skyweave$styles(),owner);
    }

    public static boolean excludes(Feature<?> feature) {
        return feature == Feature.TREE || feature == Feature.FLOWER || feature == Feature.NO_BONEMEAL_FLOWER
                || feature == Feature.RANDOM_PATCH || feature == Feature.VEGETATION_PATCH
                || feature == Feature.WATERLOGGED_VEGETATION_PATCH || feature == Feature.HUGE_RED_MUSHROOM
                || feature == Feature.HUGE_BROWN_MUSHROOM || feature == Feature.BAMBOO || feature == Feature.ROOT_SYSTEM
                || feature == Feature.VINES || feature == Feature.WEEPING_VINES || feature == Feature.TWISTING_VINES
                || feature == Feature.SCULK_PATCH;
    }
}
