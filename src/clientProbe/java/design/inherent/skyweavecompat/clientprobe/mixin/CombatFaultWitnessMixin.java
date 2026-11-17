package design.inherent.skyweavecompat.clientprobe.mixin;

import design.inherent.skyweavecompat.clientprobe.ClientCombatProbe;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
public abstract class CombatFaultWitnessMixin {
    @Inject(method = "attack", at = @At("HEAD"), require = 1, allow = 1)
    private void skyweave$controlledNativeFault(Player player, Entity target, CallbackInfo ci) {
        ClientCombatProbe.throwIfRequested();
    }
}
