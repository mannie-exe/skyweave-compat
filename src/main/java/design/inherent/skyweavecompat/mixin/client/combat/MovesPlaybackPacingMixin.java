package design.inherent.skyweavecompat.mixin.client.combat;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import design.inherent.skyweavecompat.client.combat.CombatPreferences;
import design.inherent.skyweavecompat.client.combat.ClientAttackBridge;
import design.inherent.skyweavecompat.client.combat.MovesAttackBridge;
import design.inherent.skyweavecompat.client.combat.MovesPacing;
import design.inherent.skyweavecompat.combat.AttackSnapshot;
import design.inherent.skyweavecompat.combat.AttackPacing;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import punchymoves.client.animation.AnimationLibrary;

@Mixin(targets = "punchymoves.client.animation.LocomotionController", remap = false)
public abstract class MovesPlaybackPacingMixin {
    @Shadow private int pendingPrimaryActions;
    @Unique private AttackSnapshot skyweave$pending, skyweave$playing;
    @Unique private boolean skyweave$pacing;
    @Inject(method = "tick", at = @At("HEAD"), require = 1, allow = 1)
    private void skyweave$discardInvalidatedInputs(CallbackInfo ci) {
        if (skyweave$pending != null && skyweave$pending.session() != ClientAttackBridge.session()) {
            pendingPrimaryActions = 0; skyweave$pending = null;
        }
        if (skyweave$playing != null && skyweave$playing.session() != ClientAttackBridge.session())
            skyweave$playing = null;
    }
    @Inject(method = "queuePrimaryAction", at = @At("HEAD"), require = 1, allow = 1)
    private void skyweave$queued(boolean block, float charge, int presses, boolean grounded, double fall, CallbackInfo ci) {
        skyweave$pending = MovesAttackBridge.transferring();
    }
    @Inject(method = "consumePrimaryAction", at = @At("HEAD"), require = 1, allow = 1)
    private void skyweave$consumed(CallbackInfo ci) {
        if (pendingPrimaryActions <= 0) return;
        skyweave$playing = skyweave$pending;
        skyweave$pacing = CombatPreferences.moves();
        if (pendingPrimaryActions == 1) skyweave$pending = null;
    }
    @WrapMethod(method = "primaryRetriggerReady")
    private boolean skyweave$admittedRecovery(AnimationLibrary library, ItemStack stack, boolean mining, boolean tool,
            boolean sword, boolean spear, boolean trident, Operation<Boolean> original) {
        if (!mining && AttackPacing.supportedItem(skyweave$pending)
                && skyweave$pending.session() == ClientAttackBridge.session() && CombatPreferences.moves()
                && skyweave$pending.item().equals(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())
                && MovesPacing.knownRecovery(this, tool, sword)) return true;
        return original.call(library, stack, mining, tool, sword, spear, trident);
    }
    @Inject(method = "timerControl", at = @At("RETURN"), cancellable = true, require = 1, allow = 1)
    private void skyweave$capturedClock(AnimationLibrary library, ItemStack stack, String type, @Coerce Object timeline,
            CallbackInfoReturnable<Object> cir) {
        cir.setReturnValue(MovesPacing.timer(cir.getReturnValue(), timeline, type, skyweave$playing, skyweave$pacing));
    }
}
