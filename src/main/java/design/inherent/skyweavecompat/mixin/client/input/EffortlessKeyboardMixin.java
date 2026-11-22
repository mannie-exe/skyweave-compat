package design.inherent.skyweavecompat.mixin.client.input;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import design.inherent.skyweavecompat.client.input.EffortlessRadialInput;
import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(KeyboardHandler.class)
abstract class EffortlessKeyboardMixin {
    @WrapMethod(method = "keyPress")
    private void dispatch(long window, int key, int scan, int action, int modifiers, Operation<Void> original) {
        original.call(window, key, scan, action, modifiers);
        EffortlessRadialInput.keyboard(window, key, scan, action);
    }
}
