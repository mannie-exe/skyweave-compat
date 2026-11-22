package design.inherent.skyweavecompat.clientprobe.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import design.inherent.skyweavecompat.clientprobe.ClientIpnProbe;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Retain the actual Mouse Tweaks consumer and native click transport. */
@Mixin(targets = "yalter.mousetweaks.Main", remap = false)
abstract class MouseTweaksWitnessMixin {
    @Inject(method = "onMouseScrolled", at = @At("HEAD"), require = 1, allow = 1)
    private static void wheel(Screen screen, double x, double y, double amount, CallbackInfoReturnable<Boolean> callback) {
        ClientIpnProbe.mouseTweaksWheelCalls++;
    }
    @WrapOperation(method = "rmbTweakMaybeClickSlot", at = @At(value = "INVOKE",
            target = "Lyalter/mousetweaks/IGuiScreenHandler;clickSlot(Lnet/minecraft/world/inventory/Slot;Lyalter/mousetweaks/MouseButton;Z)V"), require = 1, allow = 1)
    private static void drag(@Coerce Object handler, Slot slot, @Coerce Object button, boolean shift, Operation<Void> original) {
        original.call(handler, slot, button, shift); ClientIpnProbe.mouseTweaksDragClicks++;
    }
}
