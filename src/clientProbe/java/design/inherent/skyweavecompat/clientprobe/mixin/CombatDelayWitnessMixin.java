package design.inherent.skyweavecompat.clientprobe.mixin;

import design.inherent.skyweavecompat.clientprobe.ClientCombatProbe;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "punchy.client.state.AttackStateMachine", remap = false)
public abstract class CombatDelayWitnessMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void skyweave$holdNativeConsumer(Minecraft client, CallbackInfo ci) {
        if (ClientCombatProbe.holdPunchy()) ci.cancel();
    }
}
