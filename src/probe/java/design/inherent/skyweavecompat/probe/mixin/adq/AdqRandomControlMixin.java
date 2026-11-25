/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.probe.mixin.adq;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;

/** Identical input RNG for paired fixtures; preserves native draws and all native route decisions. */
@Pseudo
@Mixin(targets="com.ladderstar.adq.QuestGenerator",remap=false)
public abstract class AdqRandomControlMixin {
    @WrapOperation(method={"generateNewQuestAsync(Lnet/minecraft/server/level/ServerLevel;Ljava/util/UUID;)V","lambda$generateNewQuestAsync$9"},
            at=@At(value="INVOKE",target="Lnet/minecraft/util/RandomSource;create()Lnet/minecraft/util/RandomSource;"),require=2,allow=2)
    private static RandomSource seeded(Operation<RandomSource> original) {
        return RandomSource.create(Long.parseLong(System.getProperty("neighbors.probe.adqRng")));
    }
}
