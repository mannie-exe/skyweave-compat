package design.inherent.skyweavecompat.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import design.inherent.skyweavecompat.terrain.DeepDarkHabitat;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.SimpleBlockFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SimpleBlockFeature.class)
public abstract class DeepDarkPlantMixin {
    @WrapOperation(method="place",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/block/state/BlockState;canSurvive(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Z"))
    private boolean skyweave$plant(BlockState state, LevelReader level, BlockPos position, Operation<Boolean> original) {
        if (level instanceof net.minecraft.server.level.WorldGenRegion region && !DeepDarkHabitat.authored()
                && (state.getBlock() instanceof BushBlock || state.getBlock() instanceof LeavesBlock)
                && DeepDarkHabitat.contains(region,position)) return false;
        return original.call(state,level,position);
    }
}
