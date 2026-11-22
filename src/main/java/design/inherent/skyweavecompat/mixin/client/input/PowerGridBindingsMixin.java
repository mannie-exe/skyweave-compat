package design.inherent.skyweavecompat.mixin.client.input;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import design.inherent.skyweavecompat.client.input.PowerGridInput;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "org.patryk3211.powergrid.collections.ModdedKeys", remap = false)
abstract class PowerGridBindingsMixin {
    @WrapOperation(method = "matchesKey", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;matches(II)Z"), require = 1, allow = 1)
    private boolean keyboard(KeyMapping mapping, int key, int scan, Operation<Boolean> original) {
        return original.call(mapping, key, scan) && (PowerGridInput.alternate(this) || PowerGridInput.editor(mapping));
    }
    @WrapOperation(method = "matchesMouse", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;matchesMouse(I)Z"), require = 1, allow = 1)
    private boolean mouse(KeyMapping mapping, int button, Operation<Boolean> original) {
        return original.call(mapping, button) && (PowerGridInput.alternate(this) || PowerGridInput.editor(mapping));
    }
}
