package design.inherent.skyweavecompat.mixin.client.combat;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import design.inherent.skyweavecompat.client.combat.ClientAttackBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Minecraft.class)
public abstract class NativeAttackScopeMixin {
    @WrapMethod(method = "startAttack")
    private boolean skyweave$nativeAttackScope(Operation<Boolean> original) {
        var mc = (Minecraft) (Object) this;
        var frame = ClientAttackBridge.begin(mc.player, mc.hitResult);
        boolean completed = false;
        try { boolean result = original.call(); completed = true; return result; }
        finally { ClientAttackBridge.end(frame, completed); }
    }
    @WrapOperation(method = "startAttack", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;attack(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/Entity;)V"), require = 1, allow = 1)
    private void skyweave$beforeEntityAttack(MultiPlayerGameMode mode, Player player, Entity target, Operation<Void> original) {
        ClientAttackBridge.current().entity(target);
        original.call(mode, player, target);
    }
    @WrapOperation(method = "startAttack", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/player/LocalPlayer;resetAttackStrengthTicker()V"), require = 1, allow = 1)
    private void skyweave$beforeMiss(LocalPlayer player, Operation<Void> original) {
        ClientAttackBridge.current().miss(); original.call(player);
    }
    @ModifyExpressionValue(method = "startAttack", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/block/state/BlockState;isAir()Z", ordinal = 0), require = 1, allow = 1)
    private boolean skyweave$airBlockIsMiss(boolean air) {
        if (air) ClientAttackBridge.current().miss();
        return air;
    }
    @WrapOperation(method = "startAttack", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/player/LocalPlayer;swing(Lnet/minecraft/world/InteractionHand;)V"), require = 1, allow = 1)
    private void skyweave$admittedSwing(LocalPlayer player, InteractionHand hand, Operation<Void> original) {
        var frame = ClientAttackBridge.current(); frame.admit(hand);
        original.call(player, hand); frame.deliverMoves();
    }
}
