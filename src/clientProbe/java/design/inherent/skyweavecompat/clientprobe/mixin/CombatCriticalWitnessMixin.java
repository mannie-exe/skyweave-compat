package design.inherent.skyweavecompat.clientprobe.mixin;

import design.inherent.skyweavecompat.clientprobe.ClientCombatProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.world.InteractionHand;

@Mixin(targets = "punchy.client.state.AttackActionTracker", remap = false)
public abstract class CombatCriticalWitnessMixin {
    @Inject(method = "recordVisualPlayback", at = @At("RETURN"), require = 1, allow = 1)
    private static void skyweave$playback(InteractionHand hand, CallbackInfo ci) {
        ClientCombatProbe.visual(punchy.client.state.AttackActionTracker.peekVisualEpoch(),
                punchy.client.state.AttackActionTracker.peekVisualPlaybackId(), hand);
    }
    @Inject(method = "consumeCritical", at = @At("RETURN"), require = 1, allow = 1)
    private static void skyweave$consumed(CallbackInfoReturnable<Boolean> cir) {
        ClientCombatProbe.consumedCritical(cir.getReturnValueZ());
    }
}
