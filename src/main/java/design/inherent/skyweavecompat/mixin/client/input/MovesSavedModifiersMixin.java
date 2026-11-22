package design.inherent.skyweavecompat.mixin.client.input;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Restore the native modifier suffix with its dynamically registered Moves mapping. */
@Mixin(targets = "punchymoves.client.emote.EmoteKeybindManager", remap = false)
abstract class MovesSavedModifiersMixin {
    @WrapOperation(method = "install", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/InputConstants;getKey(Ljava/lang/String;)Lcom/mojang/blaze3d/platform/InputConstants$Key;"),
            require = 1, allow = 1)
    private static InputConstants.Key restore(String saved, Operation<InputConstants.Key> original,
            @Local(ordinal = 1) KeyMapping mapping) {
        int separator = saved.indexOf(':');
        var key = original.call(separator < 0 ? saved : saved.substring(0, separator));
        int end = separator < 0 ? -1 : saved.indexOf(':', separator + 1);
        var modifier = separator < 0 ? KeyModifier.NONE : KeyModifier.valueFromString(
                saved.substring(separator + 1, end < 0 ? saved.length() : end));
        // Parse first: malformed keys keep the upstream fallback, without a partial modifier edit.
        mapping.setKeyModifierAndCode(modifier, key);
        return key;
    }
}
