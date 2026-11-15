package design.inherent.skyweavecompat.mixin.water;

import com.AdamNew.dynamicwaters.HydrologyManager;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import design.inherent.skyweavecompat.water.IslandWaterTerrain;
import design.inherent.skyweavecompat.water.WaterRepairScope;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Exact foreign suffixes survive session prefixes; priority permits adapting DW's merged handlers. */
@Mixin(value = NoiseBasedChunkGenerator.class, priority = 1100)
public abstract class WaterRepairMixin {
    private static final String CARVERS = "@skyweave:watercallback(repairRiverAfterCarvers)";

    @WrapMethod(method = CARVERS, remap = false)
    private void carvers(WorldGenRegion level, long seed, RandomState random, BiomeManager biomes,
            StructureManager structures, ChunkAccess chunk, GenerationStep.Carving step,
            CallbackInfo callback, Operation<Void> original) {
        if (IslandWaterTerrain.applies((ChunkGenerator)(Object)this))
            WaterRepairScope.run(chunk, () -> original.call(level, seed, random, biomes, structures, chunk, step, callback));
        else original.call(level, seed, random, biomes, structures, chunk, step, callback);
    }

    @WrapOperation(method = CARVERS, at = @At(value = "INVOKE",
            target = "Lcom/AdamNew/dynamicwaters/HydrologyManager;calculateRiverCarving(DDIILnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/RandomState;)Lcom/AdamNew/dynamicwaters/HydrologyManager$RiverCarving;", remap = false),
            require = 1, expect = 1, allow = 1, remap = false)
    private HydrologyManager.RiverCarving proposal(double x,double z,int ground,int sea,ChunkGenerator generator,
            LevelHeightAccessor level,RandomState random,Operation<HydrologyManager.RiverCarving> original) {
        return WaterRepairScope.capture(()->original.call(x,z,ground,sea,generator,level,random));
    }

    // In DW13.1.0 only reads1/2 derive repairBottomY (-8) and maxPipeDepth (-5).
    // Other reads retain their native fluid, palette, skin and dressing semantics.
    @WrapOperation(method = CARVERS, at = {
            @At(value = "FIELD", target = "Lcom/AdamNew/dynamicwaters/HydrologyManager$RiverCarving;waterLevel:I", ordinal = 1, remap = false),
            @At(value = "FIELD", target = "Lcom/AdamNew/dynamicwaters/HydrologyManager$RiverCarving;waterLevel:I", ordinal = 2, remap = false)
    }, require = 2, expect = 2, allow = 2, remap = false)
    private int reference(HydrologyManager.RiverCarving cut, Operation<Integer> original) {
        return WaterRepairScope.reference(cut, original.call(cut));
    }

    @WrapOperation(method = CARVERS, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/chunk/ChunkAccess;setBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;"),
            require = 4, expect = 4, allow = 4, remap = false)
    private BlockState material(ChunkAccess chunk, BlockPos position, BlockState state, boolean moved,
            Operation<BlockState> original, @Local HydrologyManager.RiverCarving cut) {
        return WaterRepairScope.permits(chunk, position, cut) ? original.call(chunk, position, state, moved) : null;
    }
}
