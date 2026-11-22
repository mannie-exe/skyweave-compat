package design.inherent.skyweavecompat.mixin.client.input;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Native click admission already authorized the declared modifier; retain the legacy fallback for NONE. */
@Mixin(targets = "neoforge.nl.requios.effortlessbuilding.NeoForgeClientSetup$GameEvents", remap = false)
abstract class EffortlessHistoryMixin {
    @WrapOperation(method = "onClientTick", at = {
            @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/InputConstants;isKeyDown(JI)Z", ordinal = 0),
            @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/InputConstants;isKeyDown(JI)Z", ordinal = 1)}, require = 2, allow = 2)
    private static boolean undo(long window, int key, Operation<Boolean> original) {
        return control(EffortlessKeysAccess.skyweave$undo(), window, key, original);
    }
    @WrapOperation(method = "onClientTick", at = {
            @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/InputConstants;isKeyDown(JI)Z", ordinal = 2),
            @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/InputConstants;isKeyDown(JI)Z", ordinal = 3)}, require = 2, allow = 2)
    private static boolean redo(long window, int key, Operation<Boolean> original) {
        return control(EffortlessKeysAccess.skyweave$redo(), window, key, original);
    }
    private static boolean control(KeyMapping mapping, long window, int key, Operation<Boolean> original) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null || !mc.isWindowActive()) return false;
        return mapping.getKeyModifier() != KeyModifier.NONE || original.call(window, key);
    }
}
