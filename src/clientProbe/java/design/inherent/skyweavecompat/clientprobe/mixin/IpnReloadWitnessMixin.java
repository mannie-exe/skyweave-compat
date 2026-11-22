package design.inherent.skyweavecompat.clientprobe.mixin;

import design.inherent.skyweavecompat.clientprobe.ClientIpnProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observes the actual upstream action; never changes its outcome or consumes input. */
@Mixin(targets = "org.anti_ad.mc.ipnext.parser.CustomDataFileLoader", remap = false)
abstract class IpnReloadWitnessMixin {
    @Inject(method = "reload(Z)V", at = @At("HEAD"), require = 1)
    private void observed(boolean notify, CallbackInfo callback) {
        ClientIpnProbe.reloads++;
    }
}
