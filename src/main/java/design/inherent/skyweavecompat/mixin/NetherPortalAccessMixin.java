package design.inherent.skyweavecompat.mixin;

import design.inherent.skyweavecompat.access.DimensionAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.portal.DimensionTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NetherPortalBlock.class)
public abstract class NetherPortalAccessMixin {
    @Inject(method = "getPortalDestination", at = @At("HEAD"), cancellable = true, require = 1)
    private void skyweave$beforeExitSearch(ServerLevel source, Entity entity, BlockPos portal,
            CallbackInfoReturnable<DimensionTransition> result) {
        if (!source.dimension().equals(Level.NETHER) && DimensionAccess.denied(source.getServer(), Level.NETHER))
            result.setReturnValue(null);
    }
}
