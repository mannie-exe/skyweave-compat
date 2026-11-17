package design.inherent.skyweavecompat.mixin.client.combat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "punchymoves.client.animation.LocomotionController", remap = false)
public abstract class MovesLocalInputMixin {
    @Inject(method = "primarySwingStarted", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private static void skyweave$localAdmission(boolean local, boolean attackDown, boolean previousAttackDown,
            boolean swinging, boolean previousSwinging, int swingTime, int previousSwingTime, CallbackInfoReturnable<Boolean> cir) {
        if (local) cir.setReturnValue(false);
    }
}
