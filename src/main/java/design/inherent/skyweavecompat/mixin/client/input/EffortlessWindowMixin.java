package design.inherent.skyweavecompat.mixin.client.input;

import design.inherent.skyweavecompat.client.input.EffortlessRadialInput;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class EffortlessWindowMixin {
    @Inject(method = "setScreen", at = @At("RETURN"), require = 1)
    private void screen(net.minecraft.client.gui.screens.Screen screen, CallbackInfo callback) {
        EffortlessRadialInput.screenChanged();
    }
    @Inject(method = "setWindowActive", at = @At("HEAD"), require = 1, allow = 1)
    private void focus(boolean active, CallbackInfo callback) { if (!active) EffortlessRadialInput.reset(); }
}
