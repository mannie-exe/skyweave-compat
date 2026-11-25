/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.probe.mixin.adq;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.mojang.datafixers.util.Pair;
import design.inherent.skyweavecompat.probe.AdqSearchProbe;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets="com.ladderstar.adq.QuestGenerator",remap=false)
public abstract class AdqLocateWitnessMixin {
    @WrapOperation(method="lambda$generateNewQuestAsync$9",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/chunk/ChunkGenerator;findNearestMapStructure(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/HolderSet;Lnet/minecraft/core/BlockPos;IZ)Lcom/mojang/datafixers/util/Pair;"),require=2,allow=2)
    private static Pair<BlockPos,Holder<Structure>> locate(ChunkGenerator generator,ServerLevel level,HolderSet<Structure> targets,BlockPos origin,int radius,boolean skip,Operation<Pair<BlockPos,Holder<Structure>>> original) {
        return AdqSearchProbe.locate(()->original.call(generator,level,targets,origin,radius,skip));
    }
}
