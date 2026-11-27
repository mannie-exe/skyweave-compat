/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.mixin.client;

import java.util.Locale;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Bone names retain canonical punctuation; action and group normalization stays native. */
@Mixin(targets = "punchymoves.client.animation.ItemAnimationBindings", remap = false)
abstract class MovesOverlayBoneMixin {
    @Redirect(method = "parseOverlays", at = @At(value = "INVOKE",
            target = "Lpunchymoves/client/animation/ItemAnimationBindings;normalize(Ljava/lang/String;)Ljava/lang/String;"),
            require = 1, allow = 1)
    private static String skyweave$boneName(String bone) {
        return bone.trim().toLowerCase(Locale.ROOT);
    }
}
