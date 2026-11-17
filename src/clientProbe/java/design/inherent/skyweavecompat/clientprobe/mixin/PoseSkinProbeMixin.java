package design.inherent.skyweavecompat.clientprobe.mixin;

import design.inherent.skyweavecompat.clientprobe.ClientPoseProbe;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
abstract class PoseSkinProbeMixin {
    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true, require = 1)
    private void pose$cape(CallbackInfoReturnable<PlayerSkin> ci) {
        var cape = ClientPoseProbe.cape(((AbstractClientPlayer) (Object) this).getUUID());
        if (cape == null) return;
        var skin = ci.getReturnValue();
        ci.setReturnValue(new PlayerSkin(skin.texture(), skin.textureUrl(), cape, skin.elytraTexture(), skin.model(), skin.secure()));
    }
}
