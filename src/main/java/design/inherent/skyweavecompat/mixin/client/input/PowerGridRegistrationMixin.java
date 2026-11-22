package design.inherent.skyweavecompat.mixin.client.input;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import design.inherent.skyweavecompat.client.input.PowerGridInput;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

@Mixin(targets = "org.patryk3211.powergrid.collections.forge.ModdedKeysImpl", remap = false)
abstract class PowerGridRegistrationMixin {
    @WrapOperation(method = "register", at = @At(value = "FIELD", opcode = org.objectweb.asm.Opcodes.PUTFIELD,
            target = "Lorg/patryk3211/powergrid/collections/ModdedKeys;keybind:Lnet/minecraft/client/KeyMapping;"), require = 1, allow = 1)
    private static void context(@Coerce Object action, KeyMapping mapping, Operation<Void> original) {
        PowerGridInput.context(action, mapping); original.call(action, mapping);
    }
}
