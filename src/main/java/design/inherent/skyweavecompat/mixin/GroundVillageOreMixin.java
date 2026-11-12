package design.inherent.skyweavecompat.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import design.inherent.skyweavecompat.terrain.structure.GroundVillageTerrain;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.OreFeature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import org.sathrek.sky_archipelago.worldgen.benchmark.StructureBenchmarkRecorder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Native veins already qualify individual cells; reserved support is another ineligible target. */
@Mixin(OreFeature.class)
public abstract class GroundVillageOreMixin {
    @WrapOperation(method="doPlace",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/levelgen/feature/OreFeature;canPlaceOre(Lnet/minecraft/world/level/block/state/BlockState;Ljava/util/function/Function;Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/level/levelgen/feature/configurations/OreConfiguration;Lnet/minecraft/world/level/levelgen/feature/configurations/OreConfiguration$TargetBlockState;Lnet/minecraft/core/BlockPos$MutableBlockPos;)Z"))
    private boolean skyweave$protectVillage(BlockState state,Function<BlockPos,BlockState> states,RandomSource random,OreConfiguration configuration,
            OreConfiguration.TargetBlockState target,BlockPos.MutableBlockPos position,Operation<Boolean> original,@Local(argsOnly=true) WorldGenLevel level) {
        if(GroundVillageTerrain.featureConflict(level,position)) {
            StructureBenchmarkRecorder.increment("skyweaveVillageOreCellRefused");
            return false;
        }
        return original.call(state,states,random,configuration,target,position);
    }
}
