package design.inherent.skyweavecompat.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import design.inherent.skyweavecompat.terrain.structure.StructureFamily;
import design.inherent.skyweavecompat.terrain.structure.StructurePlacementScope;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.structures.IglooPieces;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Persist authority on each owned piece so reload does not apply vanilla's deferred Y=90 relocation again. */
@Mixin(IglooPieces.IglooPiece.class)
public abstract class StructurePolicyIglooPieceMixin {
    @Unique private boolean skyweave$anchored;

    @Inject(method = "<init>(Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplateManager;Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Rotation;I)V", at = @At("RETURN"))
    private void markOwned(CallbackInfo callback) {
        var scope = StructurePlacementScope.current();
        skyweave$anchored = scope != null && scope.family() == StructureFamily.IGLOOS;
    }

    @Inject(method = "<init>(Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplateManager;Lnet/minecraft/nbt/CompoundTag;)V", at = @At("RETURN"))
    private void readAnchor(StructureTemplateManager templates, CompoundTag tag, CallbackInfo callback) {
        skyweave$anchored = tag.getBoolean("SkyweaveIslandAnchor");
    }

    @Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
    private void saveAnchor(StructurePieceSerializationContext context, CompoundTag tag, CallbackInfo callback) {
        if (skyweave$anchored) tag.putBoolean("SkyweaveIslandAnchor", true);
    }

    @WrapOperation(method = "postProcess", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/WorldGenLevel;getHeight(Lnet/minecraft/world/level/levelgen/Heightmap$Types;II)I"))
    private int keepIslandAnchor(WorldGenLevel level, Heightmap.Types type, int x, int z, Operation<Integer> original) {
        // Vanilla subtracts 90+1 from this height. Anchored pieces already have their final terrain-relative Y.
        return skyweave$anchored ? 91 : original.call(level, type, x, z);
    }
}
