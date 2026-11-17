package design.inherent.skyweavecompat.mixin.client.combat;

import design.inherent.skyweavecompat.client.combat.ClientAttackBridge;
import design.inherent.skyweavecompat.client.combat.MovesAttackBridge;
import design.inherent.skyweavecompat.combat.AttackSnapshot;
import net.minecraft.client.player.AbstractClientPlayer;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import punchymoves.client.animation.LocomotionController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "punchymoves.client.PlayerAnimationEngine", remap = false)
public abstract class MovesAdmittedAttackMixin {
    @WrapOperation(method = "preparePlayer", at = @At(value = "INVOKE", target =
            "Lpunchymoves/client/animation/LocomotionController;queuePrimaryAction(ZFIZD)V"), require = 1, allow = 1)
    private static void skyweave$transferContext(LocomotionController controller, boolean block, float charge,
            int presses, boolean grounded, double fallDistance, Operation<Void> original) {
        MovesAttackBridge.transfer(primaryActionTrigger, () -> original.call(controller, block, charge, presses, grounded, fallDistance));
    }
    @Shadow private static boolean primaryActionBlockTarget;
    @Shadow private static float primaryActionAttackStrength;
    @Shadow private static boolean primaryActionOnGround;
    @Shadow private static double primaryActionFallDistance;
    @Shadow private static int primaryActionTrigger;
    @Inject(method = "triggerPrimaryAction", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private static void skyweave$deferNativeInput(AbstractClientPlayer player, CallbackInfo ci) {
        var frame = ClientAttackBridge.current();
        if (frame != null && frame.actor == player && !frame.deliveringMoves()) ci.cancel();
    }
    @Inject(method = "triggerPrimaryAction", at = @At("TAIL"), require = 1, allow = 1)
    private static void skyweave$capturedContext(AbstractClientPlayer player, CallbackInfo ci) {
        var frame = ClientAttackBridge.current();
        if (frame == null || frame.actor != player || !frame.deliveringMoves()) {
            MovesAttackBridge.foreign(); return;
        }
        var snapshot = frame.snapshot();
        if (snapshot == null) { MovesAttackBridge.foreign(); return; }
        primaryActionBlockTarget = snapshot.kind() == AttackSnapshot.Kind.BLOCK;
        primaryActionAttackStrength = snapshot.charge(); primaryActionOnGround = snapshot.onGround();
        primaryActionFallDistance = snapshot.fallDistance(); MovesAttackBridge.recorded(snapshot, primaryActionTrigger);
    }
}
