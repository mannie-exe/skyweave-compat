/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.probe.mixin.dh;

import com.seibel.distanthorizons.core.util.threading.PriorityTaskPicker;
import design.inherent.skyweavecompat.probe.DhWorkerStateProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Record native lifecycle calls without changing their effects or supplying work. */
@Mixin(value=PriorityTaskPicker.Executor.class, remap=false)
abstract class DhExecutorWitnessMixin {
    @Inject(method="onConfigValueSet", at=@At("HEAD"))
    private void changing(CallbackInfo ci) { DhWorkerStateProbe.event((PriorityTaskPicker.Executor)(Object)this, "before config callback"); }
    @Inject(method="shutdown", at=@At("RETURN"))
    private void stopped(CallbackInfo ci) { DhWorkerStateProbe.event((PriorityTaskPicker.Executor)(Object)this, "after native shutdown"); }
}
