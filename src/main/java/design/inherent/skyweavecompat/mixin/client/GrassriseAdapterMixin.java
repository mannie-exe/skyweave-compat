/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.mixin.client;

import design.inherent.skyweavecompat.shader.GrassriseRecipes;
import dev.grassrise.betterlighting.shader.ShaderPackAdapter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.injection.At;

/** Extend identity only; the original source/decoder/activation guards still own the transform. */
@Mixin(value = ShaderPackAdapter.class, remap = false)
public abstract class GrassriseAdapterMixin {
    @Shadow public abstract String familyId();
    @ModifyReturnValue(method = "matchesPackName", at = @At("RETURN"), require = 1, allow = 1)
    private boolean skyweave$generatedIdentity(boolean original, String name) {
        return original || familyId().equals(GrassriseRecipes.family(name));
    }
}
