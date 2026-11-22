package design.inherent.skyweavecompat.mixin.client.input;

import design.inherent.skyweavecompat.client.input.EffortlessRadialInput;
import design.inherent.skyweavecompat.client.input.EffortlessRadialScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "neoforge.nl.requios.effortlessbuilding.screen.RadialMenu", remap = false)
abstract class EffortlessRadialMixin implements EffortlessRadialScreen {
    @Inject(method = "onClose()V", at = @At("HEAD"), require = 1, allow = 1)
    private void closed(CallbackInfo callback) { EffortlessRadialInput.reset(); }
}
