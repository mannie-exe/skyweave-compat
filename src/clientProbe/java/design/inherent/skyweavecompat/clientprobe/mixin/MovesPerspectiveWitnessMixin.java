package design.inherent.skyweavecompat.clientprobe.mixin;

import design.inherent.skyweavecompat.clientprobe.ClientModifierProbe;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Counts the actual observer mutation, after any earlier producer refusal. */
@Mixin(targets = "punchymoves.client.camera.ShoulderCameraController", remap = false)
abstract class MovesPerspectiveWitnessMixin {
    @Inject(method = "onVanillaPerspectiveChange", at = @At(value = "FIELD",
            target = "Lpunchymoves/client/camera/ShoulderCameraController;vanillaPerspectiveOverride:Z",
            opcode = Opcodes.PUTSTATIC, shift = At.Shift.AFTER), require = 1, allow = 1)
    private static void observed(CallbackInfo callback) { ClientModifierProbe.perspectiveNotifications++; }
}
