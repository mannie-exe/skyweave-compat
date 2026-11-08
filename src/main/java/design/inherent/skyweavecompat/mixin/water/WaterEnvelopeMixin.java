package design.inherent.skyweavecompat.mixin.water;

import com.AdamNew.dynamicwaters.HydrologyManager;
import design.inherent.skyweavecompat.water.IslandWaterTerrain;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = HydrologyManager.class, remap = false)
public abstract class WaterEnvelopeMixin {
    @ModifyConstant(method = "carve", constant = @Constant(intValue = 320), require = 10)
    private static int ceiling(int original, double x, double z, int originalY, int seaLevel,
            ChunkGenerator generator, LevelHeightAccessor level, RandomState random, boolean whole) {
        return IslandWaterTerrain.applies(generator) ? generator.getMinY() + generator.getGenDepth() : original;
    }

    @Inject(method = "getDummyAccessor", at = @At("HEAD"), cancellable = true)
    private static void envelope(CallbackInfoReturnable<LevelHeightAccessor> result) {
        var generator = HydrologyManager.getGlobalGenerator();
        if (IslandWaterTerrain.applies(generator))
            result.setReturnValue(LevelHeightAccessor.create(generator.getMinY(), generator.getGenDepth()));
    }
}
