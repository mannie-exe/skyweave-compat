package design.inherent.skyweavecompat.clientprobe.mixin;

import design.inherent.skyweavecompat.clientprobe.ClientCombatProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import punchy.client.animation.AnimationIntentCoordinator;

@Mixin(value = AnimationIntentCoordinator.class, remap = false)
public abstract class CombatDeferredWitnessMixin {
    @Inject(method = "request", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private static void skyweave$holdDeferredAction(AnimationIntentCoordinator.Source source, Runnable action, CallbackInfo ci) {
        if (source == AnimationIntentCoordinator.Source.ATTACK && ClientCombatProbe.deferAttack(action)) ci.cancel();
    }
}
