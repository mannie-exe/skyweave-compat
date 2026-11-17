package design.inherent.skyweavecompat.mixin.client.combat;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import design.inherent.skyweavecompat.client.combat.PunchyAttackBridge;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import punchy.client.animation.AnimationIntentCoordinator;
import net.minecraft.world.item.ItemStack;

@Mixin(targets = "punchy.client.state.AttackStateMachine", remap = false)
public abstract class PunchyAttackConsumerMixin {
    @WrapOperation(method = "playAttack", at = @At(value = "INVOKE", target =
            "Lpunchy/client/animation/AnimationIntentCoordinator;request(Lpunchy/client/animation/AnimationIntentCoordinator$Source;Ljava/lang/Runnable;)V"), require = 1, allow = 1)
    private void skyweave$captureDeferredPlayback(AnimationIntentCoordinator.Source source, Runnable action,
            Operation<Void> original, @Local ItemStack selectedStack) {
        original.call(source, PunchyAttackBridge.playback(action, selectedStack));
    }
    @WrapMethod(method = "tick")
    private void skyweave$consumerScope(Minecraft client, Operation<Void> original) {
        PunchyAttackBridge.beginTick();
        try { original.call(client); } finally { PunchyAttackBridge.endTick(); }
    }
}
