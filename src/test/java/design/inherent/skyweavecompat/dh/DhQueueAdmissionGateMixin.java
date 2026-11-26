/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.dh;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Hold the original submitted loop on its original native executor, in both controls. */
@Mixin(targets = "com.seibel.distanthorizons.core.api.internal.chunkUpdating.ChunkUpdateQueueManager", remap = false)
abstract class DhQueueAdmissionGateMixin {
    @ModifyArg(method = "<init>", at = @At(value = "INVOKE",
            target = "Ljava/util/concurrent/ExecutorService;execute(Ljava/lang/Runnable;)V"), index = 0, require = 1)
    private Runnable beforeNativeLoop(Runnable original) { return DhQueueAdmissionControls.gate(original); }
}
