/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.clientprobe.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.seibel.distanthorizons.core.world.DhClientWorld;
import design.inherent.skyweavecompat.clientprobe.ClientDhLifecycleProbe;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value=DhClientWorld.class,remap=false)
abstract class DhLifecycleWorldCloseWitnessMixin {
    @WrapMethod(method="close")
    private void observed(Operation<Void> original) {
        ClientDhLifecycleProbe.worldClosing();
        try { original.call(); }
        finally { ClientDhLifecycleProbe.worldClosed(); }
    }
}
