package design.inherent.skyweavecompat.mixin.water;

import design.inherent.skyweavecompat.water.IslandWaterTerrain;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Empty sky terminates drainage; it is never a basin to fill or a route to another island. */
@Mixin(targets = "com.AdamNew.dynamicwaters.terrain.BasinTile$Builder", remap = false)
public abstract class WaterSinksMixin {
    @Shadow @Final private ChunkGenerator generator;
    @Shadow @Final private RandomState randomState;
    @Shadow @Final private int cx0;
    @Shadow @Final private int cz0;
    @Shadow @Final private float[] h;
    @Shadow @Final private boolean[] sink;

    @Inject(method = "stampSinks", at = @At("RETURN"))
    private void emptySky(CallbackInfo callback) {
        if (!IslandWaterTerrain.applies(generator)) return;
        int width = (int)Math.sqrt(h.length);
        int[] ground = new int[h.length];
        for (int i = 0; i < h.length; i++) {
            // Upstream has already added routing noise to h. Occupancy must come from
            // the unperturbed plan, otherwise empty sky becomes a false small basin.
            int x = (cx0 + i % width) * 8, z = (cz0 + i / width) * 8;
            ground[i] = IslandWaterTerrain.height(generator, randomState, x, z);
        }
        for (int i = 0; i < h.length; i++) {
            if (ground[i] != generator.getMinY()) continue;
            sink[i] = true;
            int rim = Integer.MAX_VALUE;
            for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
                int x = i % width + dx, z = i / width + dz;
                if (x < 0 || z < 0 || x >= width || z >= width) continue;
                int adjacent = ground[z * width + x];
                if (adjacent != generator.getMinY()) rim = Math.min(rim, adjacent);
            }
            // A terminal discharge is at its supported rim, not hundreds of blocks
            // down at the void floor. This is a routing boundary, never added terrain.
            if (rim != Integer.MAX_VALUE) h[i] = rim - 1;
        }
    }

    @ModifyExpressionValue(method = "settle", at = @At(value = "FIELD",
            target = "Lcom/AdamNew/dynamicwaters/terrain/BasinTile$Builder;seaLevel:I"))
    private int outletLevel(int original, @Local(ordinal = 0) int cell) {
        return IslandWaterTerrain.applies(generator) && sink[cell] ? Math.round(h[cell]) + 1 : original;
    }

    @ModifyExpressionValue(method = "emit", at = @At(value = "FIELD",
            target = "Lcom/AdamNew/dynamicwaters/terrain/BasinTile$Builder;seaLevel:I"))
    private int mouthLevel(int original, @Local(argsOnly = true) int[] cells,
            @Local(argsOnly = true, ordinal = 0) int from, @Local(argsOnly = true, ordinal = 1) int to,
            @Local(argsOnly = true, ordinal = 3) boolean mouth) {
        return IslandWaterTerrain.applies(generator) && mouth
                ? Math.max(original, Math.round(Math.min(h[cells[from]], h[cells[to]])) + 1) : original;
    }
}
