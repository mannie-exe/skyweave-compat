package design.inherent.skyweavecompat.mixin.client.input;

import design.inherent.skyweavecompat.client.input.IpnTextInput;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class IpnWindowMixin {
    @Inject(method = "setWindowActive", at = @At("HEAD"), require = 1)
    private void windowChanged(boolean active, CallbackInfo callback) {
        if (!active) IpnTextInput.quarantineHeld();
    }
}
