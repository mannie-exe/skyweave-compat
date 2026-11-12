package design.inherent.skyweavecompat.mixin;

import design.inherent.skyweavecompat.terrain.DeepDarkHabitat;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import org.spongepowered.asm.mixin.Mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

@Mixin(ConfiguredFeature.class)
public abstract class DeepDarkFeatureMixin {
    @WrapMethod(method="place")
    private boolean skyweave$naturalFeature(WorldGenLevel level, ChunkGenerator generator, RandomSource random,
            BlockPos position, Operation<Boolean> original) {
        var feature=(ConfiguredFeature<?,?>)(Object)this;
        if(!(level instanceof net.minecraft.server.level.WorldGenRegion) || DeepDarkHabitat.authored())
            return original.call(level,generator,random,position);
        boolean spreading=DeepDarkHabitat.spreading(feature);
        if ((spreading || DeepDarkHabitat.excludes(feature.feature())) && DeepDarkHabitat.contains(level,position)) return false;
        // A native patch may start in nearby void/ordinary habitat and grow across this boundary.
        return spreading ? DeepDarkHabitat.naturalSculk(()->original.call(level,generator,random,position))
                : original.call(level,generator,random,position);
    }
}
