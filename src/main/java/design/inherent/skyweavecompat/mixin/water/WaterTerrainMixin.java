package design.inherent.skyweavecompat.mixin.water;

import com.AdamNew.dynamicwaters.terrain.CoarseHeightField;
import com.AdamNew.dynamicwaters.FastConcurrentCache;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import design.inherent.skyweavecompat.water.IslandWaterTerrain;
import design.inherent.skyweavecompat.water.OwnedWaterHeights;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CoarseHeightField.class, remap = false)
public abstract class WaterTerrainMixin {
    @WrapOperation(method = {"block", "fillWindow"}, at = @At(value = "FIELD",
            target = "Lcom/AdamNew/dynamicwaters/terrain/CoarseHeightField;blocks:Lcom/AdamNew/dynamicwaters/FastConcurrentCache;"), require = 2, allow = 2)
    private static FastConcurrentCache<short[]> cache(Operation<FastConcurrentCache<short[]>> original,
            @Local(argsOnly = true) ChunkGenerator generator, @Local(argsOnly = true) RandomState random) {
        return random != null && IslandWaterTerrain.applies(generator) ? OwnedWaterHeights.cache(generator, random) : original.call();
    }

    @Inject(method = "reset", at = @At("TAIL"), require = 1)
    private static void reset(CallbackInfo ignored) { OwnedWaterHeights.reset(); }

    @Inject(method = "surfaceAt", at = @At("HEAD"), cancellable = true)
    private static void surface(int x, int z, ChunkGenerator generator, RandomState random,
            CallbackInfoReturnable<Integer> result) {
        if (random != null && IslandWaterTerrain.applies(generator)) result.setReturnValue(IslandWaterTerrain.height(generator, random, x, z));
    }

    @Inject(method = "compute", at = @At("HEAD"), cancellable = true)
    private static void block(int bx, int bz, ChunkGenerator generator, RandomState random,
            CallbackInfoReturnable<short[]> result) {
        if (random == null || !IslandWaterTerrain.applies(generator)) return;
        short[] heights = new short[CoarseHeightField.BLOCK_CELLS * CoarseHeightField.BLOCK_CELLS];
        for (int z = 0; z < CoarseHeightField.BLOCK_CELLS; z++) for (int x = 0; x < CoarseHeightField.BLOCK_CELLS; x++) {
            int worldX = ((bx << CoarseHeightField.BLOCK_SHIFT) + x) << CoarseHeightField.CELL_SHIFT;
            int worldZ = ((bz << CoarseHeightField.BLOCK_SHIFT) + z) << CoarseHeightField.CELL_SHIFT;
            heights[z * CoarseHeightField.BLOCK_CELLS + x] = (short)IslandWaterTerrain.height(generator, random, worldX, worldZ);
        }
        result.setReturnValue(heights);
    }
}
