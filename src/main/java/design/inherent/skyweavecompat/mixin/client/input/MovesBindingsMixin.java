package design.inherent.skyweavecompat.mixin.client.input;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import design.inherent.skyweavecompat.client.input.NativeInputAdmission;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Only Moves' camera/menu/emote activation comparisons use native modifier arbitration. */
@Mixin(targets = "punchymoves.client.emote.EmoteKeybindManager", remap = false)
abstract class MovesBindingsMixin {
    @WrapOperation(method = {"handleKey", "isMenuKey"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/KeyMapping;matches(II)Z"), require = 7, allow = 7)
    private static boolean matches(KeyMapping mapping, int key, int scan, Operation<Boolean> original) {
        return original.call(mapping, key, scan) && NativeInputAdmission.keyboard(mapping, key, scan);
    }
}
