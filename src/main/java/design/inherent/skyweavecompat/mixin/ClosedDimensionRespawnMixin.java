package design.inherent.skyweavecompat.mixin;

import design.inherent.skyweavecompat.access.DimensionAccess;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.DimensionTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class ClosedDimensionRespawnMixin {
    @Inject(method = "findRespawnPositionAndUseSpawnBlock", at = @At("HEAD"), cancellable = true, require = 1)
    private void skyweave$closedAnchor(boolean keep, DimensionTransition.PostDimensionTransition after,
            CallbackInfoReturnable<DimensionTransition> result) {
        var player = (ServerPlayer)(Object)this;
        if (DimensionAccess.denied(player.getServer(), player.getRespawnDimension()))
            result.setReturnValue(new DimensionTransition(player.getServer().overworld(), player, after));
    }
}
