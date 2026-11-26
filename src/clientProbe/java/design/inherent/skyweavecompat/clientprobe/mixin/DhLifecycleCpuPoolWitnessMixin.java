/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.clientprobe.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.llamalad7.mixinextras.sugar.Local;
import com.seibel.distanthorizons.core.util.objects.pooling.PhantomArrayList.*;
import design.inherent.skyweavecompat.clientprobe.ClientDhLifecycleProbe;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.concurrent.ConcurrentLinkedQueue;

@Mixin(value=PhantomArrayListPool.class, remap=false)
abstract class DhLifecycleCpuPoolWitnessMixin {
    @Shadow @Final public String name;
    @Inject(method="checkoutByteBuffers", at=@At("RETURN"), require=1, allow=1)
    private void borrowed(int count, CallbackInfoReturnable<PhantomArrayListCheckout> ci) {
        ClientDhLifecycleProbe.cpuBorrowed(name, ci.getReturnValue());
    }
    @WrapOperation(method="returnCheckout", at=@At(value="INVOKE",
            target="Ljava/util/concurrent/ConcurrentLinkedQueue;add(Ljava/lang/Object;)Z"), require=1, allow=1)
    private boolean returned(ConcurrentLinkedQueue<?> queue, Object value, Operation<Boolean> original,
            @Local(argsOnly=true) PhantomArrayListCheckout checkout) {
        Object observation = ClientDhLifecycleProbe.cpuReturning(checkout);
        boolean added = original.call(queue, value);
        if (added) ClientDhLifecycleProbe.cpuReturned(observation);
        return added;
    }
}
