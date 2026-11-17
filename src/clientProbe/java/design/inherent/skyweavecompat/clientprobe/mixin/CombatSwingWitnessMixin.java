package design.inherent.skyweavecompat.clientprobe.mixin;

import design.inherent.skyweavecompat.clientprobe.ClientCombatProbe;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

@Mixin(LivingEntity.class)
public abstract class CombatSwingWitnessMixin {
    @WrapMethod(method = "swing(Lnet/minecraft/world/InteractionHand;Z)V")
    private void skyweave$actualRemoteTransition(InteractionHand hand, boolean fromServer, Operation<Void> original) {
        var entity = (LivingEntity)(Object)this; boolean before = entity.swinging; int time = entity.swingTime;
        original.call(hand, fromServer);
        if (entity instanceof Player player) ClientCombatProbe.remoteSwing(player, fromServer, before, time, entity.swinging, entity.swingTime);
    }
    @Inject(method = "swing(Lnet/minecraft/world/InteractionHand;Z)V", at = @At("HEAD"), require = 1, allow = 1)
    private void skyweave$swing(InteractionHand hand, boolean fromServer, CallbackInfo ci) {
        if ((Object) this instanceof Player player) ClientCombatProbe.swing(player);
    }
}
