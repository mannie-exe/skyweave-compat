package design.inherent.skyweavecompat.mixin.client;

import design.inherent.skyweavecompat.client.IrisHandEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Iris owns the real hand passes; the later vanilla callbacks must not clear their shared mask. */
@Mixin(targets = {"net.hmioutline.render.MoldRenderer", "net.hmioutline.render.GlowHalo"}, remap = false)
public abstract class OutlineVanillaHandMixin {
    @Inject(method = {"beginHand", "endHand"}, at = @At("HEAD"), cancellable = true, require = 2, allow = 2)
    private static void skyweave$nativeHandOwner(CallbackInfo callback) {
        if (IrisHandEffects.active() && !IrisHandEffects.nativeCallback()) callback.cancel();
    }
}
