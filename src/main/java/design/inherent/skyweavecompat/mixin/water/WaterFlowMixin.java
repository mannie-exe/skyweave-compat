package design.inherent.skyweavecompat.mixin.water;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import design.inherent.skyweavecompat.water.IslandWaterFlow;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(FlowingFluid.class)
public abstract class WaterFlowMixin {
    @WrapMethod(method = "getFlow")
    private Vec3 context(BlockGetter blocks, BlockPos pos, FluidState state, Operation<Vec3> original) {
        return IslandWaterFlow.query(blocks, pos, state, () -> original.call(blocks, pos, state));
    }
}
