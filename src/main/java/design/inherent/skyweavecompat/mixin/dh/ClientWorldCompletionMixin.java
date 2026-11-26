/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.mixin.dh;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.seibel.distanthorizons.core.world.DhClientWorld;
import design.inherent.skyweavecompat.client.DhRenderOwner;
import design.inherent.skyweavecompat.dh.CompletionAwait;
import java.util.concurrent.CompletableFuture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Level closure can need upload completion before the later shared-executor shutdown. */
@Mixin(value=DhClientWorld.class, remap=false)
abstract class ClientWorldCompletionMixin {
    @WrapOperation(method="close", at=@At(value="INVOKE",
            target="Ljava/util/concurrent/CompletableFuture;join()Ljava/lang/Object;"), require=1)
    private Object skyweave$join(CompletableFuture<Object> future, Operation<Object> original) {
        if (future.isDone()) return original.call(future);
        var owner=DhRenderOwner.capture();
        return CompletionAwait.join(future, owner::isCurrent, owner::service, () -> original.call(future));
    }
}
