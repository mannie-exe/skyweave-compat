package design.inherent.skyweavecompat.mixin.client.input;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import design.inherent.skyweavecompat.client.input.IpnTextInput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.anti_ad.mc.ipnext.input.InventoryInputHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Native eligibility remains intact; deliberate slot mouse actions reach the owned binding guard. */
@Mixin(value = InventoryInputHandler.class, remap = false)
abstract class IpnInventoryInputMixin {
    @WrapOperation(method = "onInput", at = @At(value = "INVOKE",
            target = "Lorg/anti_ad/mc/ipnext/ingame/InventoryKt;isInputFieldActive(Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;)Z"), require = 1)
    private boolean textOwner(AbstractContainerScreen<?> screen, Operation<Boolean> original) {
        return original.call(screen) && !IpnTextInput.inventoryMouseEvent();
    }
}
