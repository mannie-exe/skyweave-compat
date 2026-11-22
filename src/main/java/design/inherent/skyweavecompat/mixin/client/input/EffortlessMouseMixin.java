package design.inherent.skyweavecompat.mixin.client.input;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import design.inherent.skyweavecompat.client.input.EffortlessRadialInput;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(MouseHandler.class)
abstract class EffortlessMouseMixin {
    @WrapMethod(method = "onPress")
    private void dispatch(long window, int button, int action, int modifiers, Operation<Void> original) {
        original.call(window, button, action, modifiers);
        EffortlessRadialInput.mouse(window, button, action);
    }
}
