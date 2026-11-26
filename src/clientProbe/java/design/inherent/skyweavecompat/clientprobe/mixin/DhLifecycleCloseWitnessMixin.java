/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.clientprobe.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.seibel.distanthorizons.core.util.threading.PriorityTaskPicker;
import design.inherent.skyweavecompat.clientprobe.ClientDhLifecycleProbe;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value=PriorityTaskPicker.class,remap=false)
abstract class DhLifecycleCloseWitnessMixin {
    @WrapMethod(method="shutdownNow")
    private void observed(Operation<Void> original) {
        var picker = (PriorityTaskPicker)(Object)this;
        long start=ClientDhLifecycleProbe.closing(picker); Throwable failure=null;
        try { original.call(); }
        catch(Throwable error) { failure=error;throw error; }
        finally { ClientDhLifecycleProbe.closed(picker,start,failure); }
    }
}
