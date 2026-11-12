package design.inherent.skyweavecompat.probe.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import design.inherent.skyweavecompat.probe.VillageTerrainTrace;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.feature.OreFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Native ore bypasses ChunkAccess.setBlockState; observe its actual section write separately. */
@Mixin(OreFeature.class)
abstract class GroundOreWriteMixin {
    private static final boolean WITNESS=!System.getProperty("neighbors.probe.villageWitness","").isEmpty();
    @WrapOperation(method="doPlace",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/chunk/LevelChunkSection;setBlockState(IIILnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState skyweave$observeOre(LevelChunkSection section,int x,int y,int z,BlockState state,boolean checked,
            Operation<BlockState> original,@Local(argsOnly=true) WorldGenLevel level,@Local BlockPos.MutableBlockPos position) {
        if(WITNESS)VillageTerrainTrace.write(level.getChunk(position),position,state);
        return original.call(section,x,y,z,state,checked);
    }
}
