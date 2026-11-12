package design.inherent.skyweavecompat.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import design.inherent.skyweavecompat.terrain.structure.GroundVillageTerrain;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import org.spongepowered.asm.mixin.Mixin;

/** Assembly-only padding must not change qualified native processor references after save/reopen. */
@Mixin(PoolElementStructurePiece.class)
public abstract class GroundVillagePieceMixin {
    @WrapMethod(method="postProcess")
    private void skyweave$groundReference(WorldGenLevel level,StructureManager structures,ChunkGenerator generator,RandomSource random,
            BoundingBox bounds,ChunkPos chunk,BlockPos reference,Operation<Void> original) {
        var piece=(PoolElementStructurePiece)(Object)this;
        GroundVillageTerrain.place(piece,()->original.call(level,structures,generator,random,bounds,chunk,
                GroundVillageTerrain.reference(piece,reference)));
    }
}
