/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.clientprobe.mixin;

import com.seibel.distanthorizons.core.render.RenderThreadTaskHandler;
import design.inherent.skyweavecompat.clientprobe.ClientDhLifecycleProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=RenderThreadTaskHandler.class,remap=false)
abstract class DhLifecycleTaskWitnessMixin {
    @Inject(method="runRenderThreadTasks()V",at=@At("HEAD"),require=1,allow=1)
    private void observed(CallbackInfo ci) { ClientDhLifecycleProbe.serviced(); }
    @Inject(method="runRenderThreadTasks(J)V",at=@At("HEAD"),require=1,allow=1)
    private void nativeService(long budget,CallbackInfo ci) { ClientDhLifecycleProbe.nativeRenderService(budget); }
}
