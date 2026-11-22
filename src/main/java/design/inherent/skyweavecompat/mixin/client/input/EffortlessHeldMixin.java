package design.inherent.skyweavecompat.mixin.client.input;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import design.inherent.skyweavecompat.client.input.EffortlessRadialInput;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(targets = "neoforge.nl.requios.effortlessbuilding.utilities.KeyBindings", remap = false)
abstract class EffortlessHeldMixin {
    @WrapMethod(method = "isKeyDown(Lnet/minecraft/client/KeyMapping;)Z")
    private static boolean down(KeyMapping mapping, Operation<Boolean> original) {
        return mapping == EffortlessKeysAccess.skyweave$radial() ? EffortlessRadialInput.down(mapping) : original.call(mapping);
    }
}
