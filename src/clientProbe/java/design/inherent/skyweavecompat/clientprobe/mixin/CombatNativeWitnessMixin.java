package design.inherent.skyweavecompat.clientprobe.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import design.inherent.skyweavecompat.clientprobe.ClientCombatProbe;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Player.class)
public abstract class CombatNativeWitnessMixin {
    @ModifyExpressionValue(method = "attack", at = @At(value = "INVOKE", target =
            "Lnet/neoforged/neoforge/common/CommonHooks;fireCriticalHit(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/Entity;ZF)Lnet/neoforged/neoforge/event/entity/player/CriticalHitEvent;"), require = 1, allow = 1)
    private CriticalHitEvent skyweave$critical(CriticalHitEvent event) {
        ClientCombatProbe.critical((Player) (Object) this, event.isCriticalHit(), event.getDamageMultiplier());
        return event;
    }
}
