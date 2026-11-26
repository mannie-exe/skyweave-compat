/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.mixin.dh;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.*;
import com.seibel.distanthorizons.core.util.objects.pooling.PhantomArrayList.*;
import design.inherent.skyweavecompat.dh.RenderBufferScope;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value=PhantomArrayListPool.class, remap=false)
abstract class RenderBufferPoolMixin {
    @Inject(method="checkoutByteBuffers", at=@At("RETURN"), require=1, allow=1)
    private void skyweave$borrow(int count, CallbackInfoReturnable<PhantomArrayListCheckout> ci) {
        var scope = RenderBufferScope.currentWorker(); if (scope == null) return;
        Object pool = this;
        if (pool == LodQuadBuilder.ARRAY_LIST_POOL || pool == LodBufferContainer.ARRAY_LIST_POOL) {
            var checkout = ci.getReturnValue(); scope.borrowed(checkout, checkout::close);
        }
    }
    @WrapMethod(method="returnCheckout")
    private void skyweave$return(PhantomArrayListCheckout checkout, Operation<Void> original) {
        RenderBufferScope.nativeReturn(checkout, () -> original.call(checkout));
    }
}
