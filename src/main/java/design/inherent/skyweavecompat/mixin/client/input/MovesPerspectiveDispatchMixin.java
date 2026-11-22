package design.inherent.skyweavecompat.mixin.client.input;

import design.inherent.skyweavecompat.client.input.MovesPerspectiveEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Defer the raw key notification; preserve controller calls outside keyboard dispatch. */
@Mixin(targets = "punchymoves.client.camera.ShoulderCameraController", remap = false)
abstract class MovesPerspectiveDispatchMixin {
    @Inject(method = "onVanillaPerspectiveChange()V", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private static void admittedChange(CallbackInfo callback) {
        if (MovesPerspectiveEvents.keyboardDispatch()) callback.cancel();
    }
}
