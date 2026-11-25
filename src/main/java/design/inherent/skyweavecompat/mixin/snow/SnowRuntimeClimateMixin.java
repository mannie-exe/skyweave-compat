package design.inherent.skyweavecompat.mixin.snow;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import design.inherent.skyweavecompat.climate.SurfaceClimateScope;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;

/** Native weather/configuration and physical light/support checks remain in their owner. */
@Mixin(targets = "snownee.snow.util.CommonProxy", remap = false)
abstract class SnowRuntimeClimateMixin {
    @WrapMethod(method = "coldEnoughToSnow")
    private static boolean skyweave$cold(LevelReader level, BlockPos position, Holder<Biome> biome,
                                         Operation<Boolean> original) {
        return SurfaceClimateScope.query(level, () -> original.call(level, position, biome));
    }
    @WrapMethod(method = "shouldMeltByTemperature(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Holder;I)Z")
    private static boolean skyweave$melt(Level level, BlockPos position, Holder<Biome> biome, int layers,
                                         Operation<Boolean> original) {
        return SurfaceClimateScope.query(level, () -> original.call(level, position, biome, layers));
    }
}
