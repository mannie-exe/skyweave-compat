package design.inherent.skyweavecompat.clientprobe.mixin;

import design.inherent.skyweavecompat.clientprobe.ClientIpnProbe;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Witness only; leaves the original sort and item movement intact. */
@Mixin(targets = "org.anti_ad.mc.ipnext.inventory.GeneralInventoryActions", remap = false)
abstract class IpnSortWitnessMixin {
    @Inject(method = "doSort(Lnet/minecraft/world/inventory/AbstractContainerMenu;ZZ)V", at = @At("HEAD"), require = 1)
    private void observed(AbstractContainerMenu menu, boolean first, boolean second, CallbackInfo callback) {
        ClientIpnProbe.sorts++;
    }
}
