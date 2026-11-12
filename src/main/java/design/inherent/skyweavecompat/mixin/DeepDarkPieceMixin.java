package design.inherent.skyweavecompat.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import design.inherent.skyweavecompat.terrain.DeepDarkHabitat;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(PoolElementStructurePiece.class)
public abstract class DeepDarkPieceMixin {
    @WrapMethod(method="postProcess")
    private void skyweave$authored(WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
            RandomSource random, BoundingBox bounds, ChunkPos chunk, BlockPos reference, Operation<Void> original) {
        DeepDarkHabitat.authored(() -> original.call(level,structures,generator,random,bounds,chunk,reference));
    }
}
