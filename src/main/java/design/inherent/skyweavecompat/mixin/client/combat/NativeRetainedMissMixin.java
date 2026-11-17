package design.inherent.skyweavecompat.mixin.client.combat;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import design.inherent.skyweavecompat.client.combat.ClientAttackBridge;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;

/** CN's retained-energy miss swings directly before its injected early return. */
@Mixin(LivingEntity.class)
public abstract class NativeRetainedMissMixin {
    @WrapMethod(method = "swing(Lnet/minecraft/world/InteractionHand;Z)V")
    private void skyweave$retainedMiss(InteractionHand hand, boolean fromServer, Operation<Void> original) {
        var frame = ClientAttackBridge.current();
        boolean admitted = !fromServer && frame != null && frame.actor == (Object) this && frame.admitMiss(hand);
        original.call(hand, fromServer);
        if (admitted) frame.deliverMoves();
    }
}
