/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.probe.mixin.adq;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import design.inherent.skyweavecompat.probe.AdqSearchProbe;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.*;

@Pseudo
@Mixin(targets="com.ladderstar.adq.QuestGenerator",remap=false)
public abstract class AdqLandingWitnessMixin {
    @WrapMethod(method="resolveDrySurface")
    private static BlockPos surface(ServerLevel level,int x,int z,Operation<BlockPos> original) {
        var result=original.call(level,x,z);AdqSearchProbe.surface(level,x,z,result);return result;
    }
}
