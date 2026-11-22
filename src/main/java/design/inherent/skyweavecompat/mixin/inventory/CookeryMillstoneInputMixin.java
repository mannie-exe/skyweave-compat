package design.inherent.skyweavecompat.mixin.inventory;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import org.spongepowered.asm.mixin.Mixin;

/** Preserve native eligibility/events/processing; adapt only the automation insertion contract. */
@Mixin(targets = "com.github.ysbbbbbb.kaleidoscopecookery.inventory.itemhandler.MillstoneInputHandler", remap = false)
abstract class CookeryMillstoneInputMixin {
    @WrapMethod(method = "insertItem")
    private ItemStack skyweave$insert(int slot, ItemStack stack, boolean simulate, Operation<ItemStack> original) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        var handler = (IItemHandler) (Object) this;
        if (slot != 0 || !handler.getStackInSlot(0).isEmpty()) return stack;
        int offered = Math.min(stack.getCount(), Math.min(handler.getSlotLimit(0), stack.getMaxStackSize()));
        var remainder = original.call(slot, stack.copyWithCount(offered), simulate);
        int accepted = offered - remainder.getCount();
        return accepted == 0 ? stack : stack.copyWithCount(stack.getCount() - accepted);
    }
}
