package design.inherent.skyweavecompat.mixin.snow;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import design.inherent.skyweavecompat.climate.SurfaceClimateScope;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import org.spongepowered.asm.mixin.Mixin;

/** Includes both precipitation classification and the native temperature predicate. */
@Mixin(targets = "bl4ckscor3.mod.snowundertrees.SnowUnderTrees", remap = false)
abstract class TreeSnowClimateMixin {
    @WrapMethod(method = "canSnow")
    private static boolean skyweave$climate(WorldGenLevel level, BlockPos position, Operation<Boolean> original) {
        return SurfaceClimateScope.query(level, () -> original.call(level, position));
    }
}
