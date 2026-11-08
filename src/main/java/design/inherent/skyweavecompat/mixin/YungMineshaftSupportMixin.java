package design.inherent.skyweavecompat.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import com.yungnickyoung.minecraft.bettermineshafts.world.generator.pieces.BetterMineshaftPiece;
import com.yungnickyoung.minecraft.yungsapi.api.world.randomize.BlockStateRandomizer;
import design.inherent.skyweavecompat.terrain.structure.IslandSupportBounds;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The physical level owns postprocessing; the placement thread scope has already ended. */
@Mixin(value = BetterMineshaftPiece.class, remap = false)
public abstract class YungMineshaftSupportMixin extends StructurePiece {
    protected YungMineshaftSupportMixin(StructurePieceType type, int depth, BoundingBox bounds) { super(type, depth, bounds); }

    @Inject(method = "generateLeg", at = @At("HEAD"))
    private void legFloor(WorldGenLevel world, RandomSource random, BoundingBox bounds, int x, int z,
            BlockStateRandomizer selector, CallbackInfo callback, @Share("skyweave$floor") LocalIntRef floor) {
        floor.set(skyweave$lowerBound(world, x, z));
    }
    @Inject(method = "generateLegOrChain", at = @At("HEAD"))
    private void chainFloor(WorldGenLevel world, RandomSource random, BoundingBox bounds, int x, int z,
            BlockStateRandomizer selector, CallbackInfoReturnable<Boolean> callback, @Share("skyweave$floor") LocalIntRef floor) {
        floor.set(skyweave$lowerBound(world, x, z));
    }
    @ModifyExpressionValue(method = {"generateLeg", "generateLegOrChain"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/WorldGenLevel;getMinBuildHeight()I"))
    private int islandFloor(int original, @Share("skyweave$floor") LocalIntRef floor) { return Math.max(original, floor.get()); }

    @Unique private int skyweave$lowerBound(WorldGenLevel world, int x, int z) {
        // Compute once per native call, not on every loop iteration. Existing rock still
        // terminates the original loop; empty sky cannot acquire a leg to the world floor.
        return IslandSupportBounds.lowerBound(world, getWorldX(x,z), getWorldY(-1), getWorldZ(x,z));
    }
}
