package design.inherent.skyweavecompat.mixin;

import design.inherent.skyweavecompat.terrain.structure.GroundVillageTerrain;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldGenRegion.class)
public abstract class GroundVillageOutputMixin {
    @Inject(method="setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",at=@At("RETURN"))
    private void skyweave$nativeOutput(BlockPos position,BlockState state,int flags,int recursion,CallbackInfoReturnable<Boolean> callback) {
        if(callback.getReturnValueZ())GroundVillageTerrain.wrote((WorldGenRegion)(Object)this,position,state);
    }
}
