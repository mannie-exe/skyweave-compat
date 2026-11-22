package design.inherent.skyweavecompat.mixin.client.input;

import design.inherent.skyweavecompat.client.input.IpnTextInput;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.anti_ad.mc.common.gui.NativeInputContextBase;
import org.anti_ad.mc.common.input.GlobalInputHandler;
import org.spongepowered.asm.mixin.Mixin;

/** Observe the native post-update dispatch; never cancel callbacks or alter upstream key sets. */
@Mixin(value = GlobalInputHandler.class, remap = false)
abstract class IpnInputDispatchMixin {
    @WrapMethod(method = "onInput(Lorg/anti_ad/mc/common/gui/NativeInputContextBase;)Z")
    private boolean dispatch(NativeInputContextBase<?> context, Operation<Boolean> original) {
        IpnTextInput.observe();
        try { return original.call(context); }
        finally { IpnTextInput.completed(); }
    }
}
