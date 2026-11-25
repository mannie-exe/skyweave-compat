/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.mixin.client;

import dev.grassrise.betterlighting.shader.RuntimeGate;
import dev.grassrise.betterlighting.shader.ShaderPackAdapter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value=RuntimeGate.class,remap=false)
public interface GrassriseGateAccess {
    @Accessor("activeAdapter") static void skyweave$active(ShaderPackAdapter value) { throw new AssertionError(); }
}
