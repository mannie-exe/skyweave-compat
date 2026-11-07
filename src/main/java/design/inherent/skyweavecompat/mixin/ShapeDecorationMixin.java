package design.inherent.skyweavecompat.mixin;

import design.inherent.skyweavecompat.terrain.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.CaveFeatures;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Place the registered vanilla sculk patch at selected high-island surfaces, through native feature logic. */
@Mixin(value = SkyIslandChunkGenerator.class, remap = false)
public abstract class ShapeDecorationMixin {
    @Inject(method = "applyBiomeDecoration", at = @At("TAIL"))
    private void sculkSurface(WorldGenLevel level, ChunkAccess chunk, StructureManager manager, CallbackInfo callback) {
        var generator = (SkyIslandChunkGenerator)(Object)this;
        var geological = (GeologicalGenerator)(Object)this;
        if (!geological.skyweave$isEnabled()) return;
        var field = geological.skyweave$field(level.getLevel().getChunkSource().randomState());
        var styles = ((StyledTerrain)(Object)field).skyweave$styles();
        if (styles == null || !styles.profile().enabled()) return;
        long seed = level.getSeed() ^ chunk.getPos().toLong() ^ 0x37bdc591L;
        if (IslandStyles.unit(seed, 1) >= styles.profile().fractures().sculkPatchChance()) return;
        var random = RandomSource.create(seed);
        var settings = generator.resolvedSkyIslandSettings();
        var snapshot = field.sampleChunkTerrainSnapshot(chunk.getPos(), settings);
        for (int attempt = 0; attempt < 4; attempt++) {
            int x = chunk.getPos().getMinBlockX() + 4 + random.nextInt(8);
            int z = chunk.getPos().getMinBlockZ() + 4 + random.nextInt(8);
            var column = snapshot.columnDataAt(x, z);
            var owner = column.dominantDescriptor();
            if (owner == null || styles.style(owner).kind() != IslandStyles.Kind.DEEP_DARK || column.segments().isEmpty()) continue;
            int y = column.segments().getFirst().topY() + 1;
            var pos = new BlockPos(x, y, z);
            if (!level.getBlockState(pos.below()).isSolidRender(level, pos.below())) continue;
            level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).getHolderOrThrow(CaveFeatures.SCULK_PATCH_DEEP_DARK)
                    .value().place(level, generator, random, pos);
            break;
        }
    }
}
