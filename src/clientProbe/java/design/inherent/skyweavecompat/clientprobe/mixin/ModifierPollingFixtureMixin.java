package design.inherent.skyweavecompat.clientprobe.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import design.inherent.skyweavecompat.clientprobe.ClientModifierProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Explicit fixture physical-input source; leaves the actual admission/consumer chains intact. */
@Mixin(InputConstants.class)
abstract class ModifierPollingFixtureMixin {
    @Inject(method = "isKeyDown", at = @At("HEAD"), cancellable = true)
    private static void held(long window, int key, CallbackInfoReturnable<Boolean> callback) {
        if (ClientModifierProbe.controlledInput) callback.setReturnValue(ClientModifierProbe.heldKeys.contains(key));
    }
}
