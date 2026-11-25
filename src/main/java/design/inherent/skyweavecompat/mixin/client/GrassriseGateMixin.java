/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.mixin.client;

import design.inherent.skyweavecompat.shader.GrassriseRecipes;
import dev.grassrise.betterlighting.shader.RuntimeGate;
import dev.grassrise.betterlighting.shader.ShaderPackAdapter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = RuntimeGate.class, remap = false)
public abstract class GrassriseGateMixin {
    @Shadow private static ShaderPackAdapter activeAdapter;
    @ModifyReturnValue(method = "hasPhotonDefaultMaterial", at = @At("RETURN"), require = 1, allow = 1)
    private static boolean skyweave$photonMaterial(boolean original) {
        // This exact Photon 1.4 output leaves grass unassigned, using native material zero.
        return original || activeAdapter == ShaderPackAdapter.PHOTON && "photon".equals(GrassriseRecipes.family(RuntimeGate.currentShaderPackName()));
    }
}
