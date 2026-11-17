package design.inherent.skyweavecompat.clientprobe.mixin;

import design.inherent.skyweavecompat.clientprobe.ClientCombatProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "punchymoves.client.animation.LocomotionController", remap = false)
public abstract class CombatRemoteInputWitnessMixin {
    @Inject(method = "primarySwingStarted", at = @At("RETURN"), require = 1, allow = 1)
    private static void skyweave$remoteInput(boolean local, boolean down, boolean previousDown, boolean swing,
            boolean previousSwing, int time, int previousTime, CallbackInfoReturnable<Boolean> cir) {
        ClientCombatProbe.remoteEdge(local, cir.getReturnValueZ());
    }
}
