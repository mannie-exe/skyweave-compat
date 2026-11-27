/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.mixin.client;

import design.inherent.skyweavecompat.client.CombatPitch;
import dev.kosmx.playerAnim.api.layered.modifier.AdjustmentModifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Retain the original modifier, fade, attack clock, and source ownership. */
@Mixin(targets = "net.bettercombat.client.animation.AttackAnimationSubStack", remap = false)
abstract class CombatPitchMixin {
    @Inject(method = "<init>(Ldev/kosmx/playerAnim/api/layered/modifier/AdjustmentModifier;)V",
            at = @At("RETURN"), require = 1, allow = 1)
    private void skyweave$attackPitch(AdjustmentModifier modifier, CallbackInfo ci) {
        var access = (CombatAdjustmentAccess) (Object) modifier;
        access.skyweave$source(CombatPitch.source(access.skyweave$source()));
    }
}
