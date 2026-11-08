package design.inherent.skyweavecompat.mixin.water;

import com.AdamNew.dynamicwaters.terrain.CoarseHeightField;
import design.inherent.skyweavecompat.water.IslandWaterTerrain;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CoarseHeightField.class, remap = false)
public abstract class WaterTerrainMixin {
    @Inject(method = "surfaceAt", at = @At("HEAD"), cancellable = true)
    private static void surface(int x, int z, ChunkGenerator generator, RandomState random,
            CallbackInfoReturnable<Integer> result) {
        if (IslandWaterTerrain.applies(generator)) result.setReturnValue(IslandWaterTerrain.height(generator, random, x, z));
    }

    @Inject(method = "compute", at = @At("HEAD"), cancellable = true)
    private static void block(int bx, int bz, ChunkGenerator generator, RandomState random,
            CallbackInfoReturnable<short[]> result) {
        if (!IslandWaterTerrain.applies(generator)) return;
        short[] heights = new short[CoarseHeightField.BLOCK_CELLS * CoarseHeightField.BLOCK_CELLS];
        for (int z = 0; z < CoarseHeightField.BLOCK_CELLS; z++) for (int x = 0; x < CoarseHeightField.BLOCK_CELLS; x++) {
            int worldX = ((bx << CoarseHeightField.BLOCK_SHIFT) + x) << CoarseHeightField.CELL_SHIFT;
            int worldZ = ((bz << CoarseHeightField.BLOCK_SHIFT) + z) << CoarseHeightField.CELL_SHIFT;
            heights[z * CoarseHeightField.BLOCK_CELLS + x] = (short)IslandWaterTerrain.height(generator, random, worldX, worldZ);
        }
        result.setReturnValue(heights);
    }
}
