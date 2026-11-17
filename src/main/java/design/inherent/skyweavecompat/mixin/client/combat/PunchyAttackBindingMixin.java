package design.inherent.skyweavecompat.mixin.client.combat;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import design.inherent.skyweavecompat.client.combat.PunchyAttackBridge;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "punchy.client.state.AttackActionTracker", remap = false)
public abstract class PunchyAttackBindingMixin {
    @Inject(method = "recordVisualPlayback", at = @At("RETURN"), require = 1, allow = 1)
    private static void skyweave$bindActualPlayback(InteractionHand hand, CallbackInfo ci) {
        PunchyAttackBridge.visualPlayback();
    }
    @WrapMethod(method = "original$recordAttack()V")
    private static void skyweave$recordMain(Operation<Void> original) {
        original.call(); PunchyAttackBridge.produced();
    }
    @WrapMethod(method = "original$recordAttack(Lnet/minecraft/world/InteractionHand;)V")
    private static void skyweave$recordHand(InteractionHand hand, Operation<Void> original) {
        original.call(hand); PunchyAttackBridge.produced();
    }
    @WrapMethod(method = "consumeHand")
    private static InteractionHand skyweave$consumeHand(Operation<InteractionHand> original) {
        var hand = original.call(); PunchyAttackBridge.consumed(hand); return hand;
    }
    @ModifyReturnValue(method = "original$consumeCritical", at = @At("RETURN"), require = 1, allow = 1)
    private static boolean skyweave$beforePlayedHook(boolean original) { return PunchyAttackBridge.critical(original); }
    @WrapMethod(method = "clear")
    private static void skyweave$discard(Operation<Void> original) {
        try { original.call(); } finally { PunchyAttackBridge.clear(); }
    }
}
