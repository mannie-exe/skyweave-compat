package design.inherent.skyweavecompat.mixin.client.input;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import design.inherent.skyweavecompat.client.input.MovesPerspectiveEvents;
import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;

/** Keep the raw keyboard observer distinguishable, including cancellation and nested dispatch. */
@Mixin(KeyboardHandler.class)
abstract class MovesKeyboardScopeMixin {
    @WrapMethod(method = "keyPress")
    private void dispatch(long window, int key, int scan, int action, int modifiers, Operation<Void> original) {
        boolean previous = MovesPerspectiveEvents.enterKeyboard();
        try { original.call(window, key, scan, action, modifiers); }
        finally { MovesPerspectiveEvents.leaveKeyboard(previous); }
    }
}
