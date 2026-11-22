package design.inherent.skyweavecompat.mixin.client.input;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import punchymoves.client.camera.ShoulderCameraController;

/** Observe the real native perspective transition once, after its accepted click is consumed. */
@Mixin(Minecraft.class)
abstract class MovesNativePerspectiveMixin {
    @Inject(method = "handleKeybinds", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Options;setCameraType(Lnet/minecraft/client/CameraType;)V", shift = At.Shift.AFTER),
            require = 1, allow = 1)
    private void changedPerspective(CallbackInfo callback) {
        ShoulderCameraController.onVanillaPerspectiveChange();
    }
}
