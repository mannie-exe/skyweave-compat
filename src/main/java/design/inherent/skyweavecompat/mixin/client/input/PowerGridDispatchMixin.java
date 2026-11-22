package design.inherent.skyweavecompat.mixin.client.input;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.platform.InputConstants;
import design.inherent.skyweavecompat.client.input.PowerGridInput;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

@Mixin(targets = "org.patryk3211.powergrid.PowerGridClient", remap = false)
abstract class PowerGridDispatchMixin {
    @WrapOperation(method = "keyPress", at = @At(value = "INVOKE",
            target = "Lorg/patryk3211/powergrid/collections/ModdedKeys;matchesKey(II)Z"), require = 1, allow = 1)
    private static boolean keyboard(@Coerce Object action, int key, int scan, Operation<Boolean> original,
            @Local(argsOnly = true, ordinal = 2) int phase) {
        var mapping = ((PowerGridKeyAccess) action).skyweave$key();
        return PowerGridInput.alternate(mapping, InputConstants.getKey(key, scan), phase, original.call(action, key, scan));
    }
    @WrapOperation(method = "mousePress", at = @At(value = "INVOKE",
            target = "Lorg/patryk3211/powergrid/collections/ModdedKeys;matchesMouse(I)Z"), require = 1, allow = 1)
    private static boolean mouse(@Coerce Object action, int button, Operation<Boolean> original,
            @Local(argsOnly = true, ordinal = 1) int phase) {
        var mapping = ((PowerGridKeyAccess) action).skyweave$key();
        return PowerGridInput.alternate(mapping, InputConstants.Type.MOUSE.getOrCreate(button), phase, original.call(action, button));
    }
    @WrapOperation(method = {"keyPress", "mousePress"}, at = @At(value = "INVOKE",
            target = "Lorg/patryk3211/powergrid/electricity/wire/ClientWireInteractions;alternatePlacementCheck(Lnet/minecraft/client/Minecraft;I)V"), require = 2, allow = 2)
    private static void observe(Minecraft mc, int phase, Operation<Void> original) {
        try { original.call(mc, phase); }
        finally { PowerGridInput.observed(phase); }
    }
}
