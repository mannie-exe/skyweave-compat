package design.inherent.skyweavecompat.mixin;

import design.inherent.skyweavecompat.access.DimensionAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The direct void overload otherwise resets camera/riding before changeDimension can refuse it. */
@Mixin(ServerPlayer.class)
public abstract class ClosedDimensionTeleportMixin {
    @Inject(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDFF)V",
            at = @At("HEAD"), cancellable = true, require = 1)
    private void skyweave$beforePlayerSideEffects(ServerLevel destination, double x, double y, double z,
            float yaw, float pitch, CallbackInfo result) {
        if (DimensionAccess.denied(destination.getServer(), destination.dimension())) result.cancel();
    }
}
