package design.inherent.skyweavecompat.clientprobe.mixin;

import design.inherent.skyweavecompat.clientprobe.ClientCombatProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import punchymoves.client.animation.AnimationLibrary;

@Mixin(targets = "punchymoves.client.animation.LocomotionController$OneShotLayer", remap = false)
public abstract class CombatMovesClockWitnessMixin {
    @Shadow private AnimationLibrary.Clip clip;
    @Shadow private float time;
    @Inject(method = "tick(Z)V", at = @At("HEAD"), require = 1, allow = 1)
    private void skyweave$clock(boolean enabled, CallbackInfo ci) {
        if (clip != null && enabled) ClientCombatProbe.clock(this, clip.name(), clip.length(), time);
    }
}
