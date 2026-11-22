package design.inherent.skyweavecompat.mixin.client.input;

import design.inherent.skyweavecompat.client.input.IpnTextInput;
import org.anti_ad.mc.common.input.AlternativeKeybind;
import org.anti_ad.mc.common.input.IKeybind;
import org.anti_ad.mc.common.input.MainKeybind;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Guard IPN-owned queries, including its config listener and tick polling; bookkeeping still runs. */
@Mixin(value = {MainKeybind.class, AlternativeKeybind.class}, remap = false)
abstract class IpnKeybindMixin {
    @Inject(method = {"isActivated()Z", "isPressing()Z"}, at = @At("HEAD"), cancellable = true, require = 1)
    private void eligible(CallbackInfoReturnable<Boolean> result) {
        if (!IpnTextInput.allowed((IKeybind)(Object)this)) result.setReturnValue(false);
    }
}
