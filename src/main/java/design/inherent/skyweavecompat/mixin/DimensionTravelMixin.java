package design.inherent.skyweavecompat.mixin;

import design.inherent.skyweavecompat.access.DimensionAccess;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.level.portal.DimensionTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Both concrete native owners override these paths; neither body may start a denied move. */
@Mixin({Entity.class, ServerPlayer.class})
public abstract class DimensionTravelMixin {
    @Inject(method = "changeDimension", at = @At("HEAD"), cancellable = true, require = 1)
    private void skyweave$dimensionTransition(DimensionTransition transition, CallbackInfoReturnable<Entity> result) {
        if (DimensionAccess.denied(transition.newLevel().getServer(), transition.newLevel().dimension())) result.setReturnValue(null);
    }
    @Inject(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FF)Z",
            at = @At("HEAD"), cancellable = true, require = 1)
    private void skyweave$directTeleport(ServerLevel destination, double x, double y, double z,
            Set<RelativeMovement> relative, float yaw, float pitch, CallbackInfoReturnable<Boolean> result) {
        if (DimensionAccess.denied(destination.getServer(), destination.dimension())) result.setReturnValue(false);
    }
}
