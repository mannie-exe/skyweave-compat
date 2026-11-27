/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import design.inherent.skyweavecompat.client.CombatPitch;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.kosmx.playerAnim.impl.animation.AnimationApplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Correct only the original native model-part rotation query, never arbitrary library reads. */
@Mixin(value = AnimationApplier.class, remap = false)
abstract class CombatArmViewMixin {
    @WrapOperation(method = "updatePart", at = @At(value = "INVOKE", ordinal = 1,
            target = "Ldev/kosmx/playerAnim/impl/animation/AnimationApplier;get3DTransform(Ljava/lang/String;Ldev/kosmx/playerAnim/api/TransformType;Ldev/kosmx/playerAnim/core/util/Vec3f;)Ldev/kosmx/playerAnim/core/util/Vec3f;"),
            require = 1, allow = 1)
    private Vec3f skyweave$attackArm(AnimationApplier applier, String part, TransformType type, Vec3f input,
            Operation<Vec3f> nativeRead) {
        return CombatPitch.arm(applier, part, type, input, nativeRead.call(applier, part, type, input));
    }
}
