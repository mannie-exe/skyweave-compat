package design.inherent.skyweavecompat.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import design.inherent.skyweavecompat.terrain.structure.IslandSupportBounds;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native marker conversion and solid/fluid checks remain intact; only the support floor changes. */
@Mixin(targets = {"com.yungnickyoung.minecraft.betterwitchhuts.world.processor.LegProcessor",
        "com.yungnickyoung.minecraft.betterwitchhuts.world.processor.FenceLegProcessor"}, remap = false)
public abstract class YungHutSupportMixin {
    @Inject(method = "processBlock", at = @At("HEAD"))
    private void supportFloor(LevelReader world, BlockPos origin, BlockPos pivot, StructureBlockInfo template,
            StructureBlockInfo placed, StructurePlaceSettings settings, CallbackInfoReturnable<StructureBlockInfo> result,
            @Share("skyweave$floor") LocalIntRef floor) {
        var state = placed.state();
        if (state.is(Blocks.BROWN_STAINED_GLASS) || state.is(Blocks.CRIMSON_FENCE)) {
            var pos = placed.pos();
            floor.set(IslandSupportBounds.lowerBound(world, pos.getX(), pos.getY(), pos.getZ()));
        }
    }

    @ModifyExpressionValue(method = "processBlock", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/LevelReader;getMinBuildHeight()I"))
    private int boundedFloor(int original, @Share("skyweave$floor") LocalIntRef floor) {
        return Math.max(original, floor.get());
    }
}
