package design.inherent.skyweavecompat.terrain;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import java.util.List;
import java.util.stream.Stream;

/** Small additions to upstream's surface palette, after its override selection. */
public final class BiomeMaterials {
    public static final TagKey<Biome> MUD = TagKey.create(Registries.BIOME,
            ResourceLocation.parse("skyweave_compat:surface_mud"));
    private static final List<TagKey<Biome>> UPSTREAM_OVERRIDES = Stream.of(
            "mushroom", "badlands", "sand", "gravel", "coarse_dirt", "snow")
            .map(name -> TagKey.create(Registries.BIOME, ResourceLocation.parse("sky_archipelago:surface_" + name))).toList();
    private BiomeMaterials() {}

    public static BlockState surface(BlockState selected, Holder<Biome> biome, int depth) {
        if (depth >= 0 && depth <= 2 && biome.is(MUD)
                && UPSTREAM_OVERRIDES.stream().noneMatch(biome::is)
                && (selected.is(Blocks.GRASS_BLOCK) || selected.is(Blocks.DIRT)))
            return Blocks.MUD.defaultBlockState();
        return selected;
    }
}
