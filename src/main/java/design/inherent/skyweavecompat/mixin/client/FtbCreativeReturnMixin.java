package design.inherent.skyweavecompat.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Match FTB's restored screen to the menu left by native container closing. */
@Mixin(targets = "dev.ftb.mods.ftblibrary.ui.BaseScreen", remap = false)
public abstract class FtbCreativeReturnMixin {
    @WrapOperation(method = "closeGui(Z)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Minecraft;setScreen(Lnet/minecraft/client/gui/screens/Screen;)V",
            remap = true), require = 1, allow = 1)
    private void skyweave$restoreCreativeMenu(Minecraft minecraft, Screen previous, Operation<Void> original) {
        if (previous instanceof CreativeModeInventoryScreen creative && minecraft.player != null
                && minecraft.gameMode != null && minecraft.gameMode.hasInfiniteItems()
                && minecraft.player.containerMenu == minecraft.player.inventoryMenu) {
            minecraft.player.containerMenu = creative.getMenu();
        } else if (previous instanceof AbstractContainerScreen<?> container
                && !(previous instanceof CreativeModeInventoryScreen) && !(previous instanceof InventoryScreen)
                && minecraft.player != null && minecraft.player.containerMenu == minecraft.player.inventoryMenu
                && container.getMenu() != minecraft.player.inventoryMenu) {
            // The server-owned menu was closed normally. Its old screen must not be revived.
            previous = null;
        }
        original.call(minecraft, previous);
    }
}
