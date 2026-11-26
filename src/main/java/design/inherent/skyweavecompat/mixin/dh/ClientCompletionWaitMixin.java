package design.inherent.skyweavecompat.mixin.dh;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.seibel.distanthorizons.core.util.threading.PriorityTaskPicker;
import design.inherent.skyweavecompat.client.DhRenderOwner;
import design.inherent.skyweavecompat.dh.CompletionAwait;
import java.util.concurrent.TimeUnit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Leave DH's deadline/fallback intact while its actual render owner completes queued uploads. */
@Mixin(value = PriorityTaskPicker.class, remap = false)
abstract class ClientCompletionWaitMixin {
    @WrapOperation(method = "shutdownNow", at = @At(value = "INVOKE",
            target = "Lcom/seibel/distanthorizons/core/util/threading/PriorityTaskPicker$Executor;awaitTermination(JLjava/util/concurrent/TimeUnit;)Z"), require = 1)
    private boolean skyweave$await(PriorityTaskPicker.Executor executor, long amount, TimeUnit unit,
            Operation<Boolean> original) throws InterruptedException {
        if (executor.isTerminated()) return original.call(executor, amount, unit);
        var owner = DhRenderOwner.capture();
        return CompletionAwait.await(executor, amount, unit, owner::isCurrent, owner::service,
                (remaining, remainingUnit) -> original.call(executor, remaining, remainingUnit));
    }
}
