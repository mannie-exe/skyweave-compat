package design.inherent.skyweavecompat.probe.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import design.inherent.skyweavecompat.probe.VillageTerrainTrace;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import org.spongepowered.asm.mixin.Mixin;

/** Separate authored piece features/clearing from ordinary biome decoration in exact-point evidence. */
@Mixin(PoolElementStructurePiece.class)
abstract class GroundPieceTraceMixin {
    private static final boolean WITNESS=!System.getProperty("neighbors.probe.villageWitness","").isEmpty();
    @WrapMethod(method="postProcess")
    private void skyweave$tracePiece(WorldGenLevel level,StructureManager structures,ChunkGenerator generator,RandomSource random,
            BoundingBox bounds,ChunkPos chunk,BlockPos reference,Operation<Void> original) {
        if(!WITNESS) {original.call(level,structures,generator,random,bounds,chunk,reference);return;}
        VillageTerrainTrace.place((PoolElementStructurePiece)(Object)this,
                ()->original.call(level,structures,generator,random,bounds,chunk,reference));
    }
}
