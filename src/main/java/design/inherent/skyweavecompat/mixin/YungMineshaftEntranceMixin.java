package design.inherent.skyweavecompat.mixin;

import com.yungnickyoung.minecraft.bettermineshafts.world.generator.pieces.VerticalEntrance;
import design.inherent.skyweavecompat.terrain.GenerationProfile;
import design.inherent.skyweavecompat.terrain.structure.StructureWorldScope;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

/** Entrance center is absolute; the upper reservation is the fixed dimension ceiling. */
@Mixin(value = VerticalEntrance.class, remap = false)
public abstract class YungMineshaftEntranceMixin extends StructurePiece {
    @Shadow @Final @Mutable private BlockPos centerPos;
    protected YungMineshaftEntranceMixin(StructurePieceType type, int depth, BoundingBox bounds) { super(type, depth, bounds); }

    @Override public void move(int dx, int dy, int dz) {
        super.move(dx, dy, dz);
        if (StructureWorldScope.current() == null) return;
        centerPos = centerPos.offset(dx, dy, dz);
        // Native postprocessing discovers the exit from actual terrain later. Keep that
        // search reservation through the dimension ceiling, without translating the ceiling.
        var bounds = getBoundingBox();
        boundingBox = new BoundingBox(bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(),
                Math.max(bounds.minY(), GenerationProfile.MAX_Y - 1), bounds.maxZ());
    }
}
