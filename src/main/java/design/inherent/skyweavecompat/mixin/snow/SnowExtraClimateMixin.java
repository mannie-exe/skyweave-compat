package design.inherent.skyweavecompat.mixin.snow;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import design.inherent.skyweavecompat.climate.SurfaceClimateScope;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;

/** The extra feature runs after vanilla shouldSnow has returned from its climate scope. */
@Mixin(targets = "snownee.snow.Hooks", remap = false)
abstract class SnowExtraClimateMixin {
    @WrapMethod(method = "placeFeatureExtra")
    private static void skyweave$climate(Biome biome, WorldGenLevel level, BlockPos position, BlockPos below,
                                        Operation<Void> original) {
        SurfaceClimateScope.run(level, () -> original.call(biome, level, position, below));
    }
}
