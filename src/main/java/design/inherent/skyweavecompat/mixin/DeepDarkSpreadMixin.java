package design.inherent.skyweavecompat.mixin;

import design.inherent.skyweavecompat.terrain.DeepDarkHabitat;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Only native worldgen sculk spreading; gameplay catalysts and authored structures stay native. */
@Mixin(WorldGenRegion.class)
public abstract class DeepDarkSpreadMixin {
    @Inject(method="setBlock",at=@At("HEAD"),cancellable=true)
    private void skyweave$naturalSpread(BlockPos position,BlockState state,int flags,int recursion,
            CallbackInfoReturnable<Boolean> result) {
        if(DeepDarkHabitat.protectsSculk((WorldGenRegion)(Object)this,position))result.setReturnValue(false);
    }
}
