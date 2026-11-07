package design.inherent.skyweavecompat.mixin;

import design.inherent.skyweavecompat.terrain.structure.StructureFamily;
import design.inherent.skyweavecompat.terrain.structure.StructurePlacementScope;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.levelgen.structure.ScatteredFeaturePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** An accepted island anchor must not fall to the void when one footprint column is empty. */
@Mixin(value = ScatteredFeaturePiece.class, priority = 1100)
public abstract class StructurePolicyPyramidPieceMixin {
    @Unique private boolean skyweave$anchoredPyramid;

    @Inject(method = "<init>(Lnet/minecraft/world/level/levelgen/structure/pieces/StructurePieceType;IIIIIILnet/minecraft/core/Direction;)V", at = @At("RETURN"))
    private void markOwned(CallbackInfo callback) {
        var scope = StructurePlacementScope.current();
        skyweave$anchoredPyramid = scope != null && scope.family() == StructureFamily.DESERT_TEMPLES;
    }

    @Inject(method = "<init>(Lnet/minecraft/world/level/levelgen/structure/pieces/StructurePieceType;Lnet/minecraft/nbt/CompoundTag;)V", at = @At("RETURN"))
    private void readAnchor(StructurePieceType type, CompoundTag tag, CallbackInfo callback) {
        skyweave$anchoredPyramid = tag.getBoolean("SkyweavePyramidAnchor");
    }

    @Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
    private void saveAnchor(StructurePieceSerializationContext context, CompoundTag tag, CallbackInfo callback) {
        if (skyweave$anchoredPyramid) tag.putBoolean("SkyweavePyramidAnchor", true);
    }

    @Inject(method = "updateHeightPositionToLowestGroundHeight", at = @At("HEAD"), cancellable = true)
    private void keepFittedAnchor(LevelAccessor level, int offset, CallbackInfoReturnable<Boolean> result) {
        if (skyweave$anchoredPyramid) result.setReturnValue(true);
    }
}
