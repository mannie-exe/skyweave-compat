package design.inherent.skyweavecompat.mixin.client.combat;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import design.inherent.skyweavecompat.client.combat.ClientAttackBridge;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Player.class)
public abstract class NativeCriticalResultMixin {
    @ModifyExpressionValue(method = "attack", at = @At(value = "INVOKE", target =
            "Lnet/neoforged/neoforge/common/CommonHooks;fireCriticalHit(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/Entity;ZF)Lnet/neoforged/neoforge/event/entity/player/CriticalHitEvent;"), require = 1, allow = 1)
    private CriticalHitEvent skyweave$actualCritical(CriticalHitEvent result, Entity target) {
        ClientAttackBridge.critical((Player) (Object) this, target, result.isCriticalHit()); return result;
    }
}
