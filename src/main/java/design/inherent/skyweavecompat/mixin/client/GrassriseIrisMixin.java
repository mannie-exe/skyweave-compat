/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.mixin.client;

import design.inherent.skyweavecompat.shader.GrassriseRecipes;
import net.irisshaders.iris.Iris;
import net.neoforged.fml.loading.FMLPaths;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Iris.class, remap = false)
public abstract class GrassriseIrisMixin {
    @Inject(method = "loadShaderpack", at = @At("HEAD"), require = 1, allow = 1)
    private static void skyweave$begin(CallbackInfo callback) {
        GrassriseRecipes.clear();
        GrassriseGateAccess.skyweave$active(null);
    }
    // Recipe assembly runs at loadShaderpack HEAD. Admit its result only afterward,
    // immediately before Iris opens the selected archive and preprocesses its sources.
    @Inject(method = "loadExternalShaderpack", at = @At("HEAD"), require = 1, allow = 1)
    private static void skyweave$admit(String selected, CallbackInfoReturnable<Boolean> callback) {
        GrassriseRecipes.load(FMLPaths.GAMEDIR.get(), selected);
    }
    @Inject(method = "destroyEverything", at = @At("HEAD"), require = 1, allow = 1)
    private static void skyweave$retire(CallbackInfo callback) {
        GrassriseRecipes.clear(); GrassriseGateAccess.skyweave$active(null);
    }
}
