package design.inherent.skyweavecompat.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import design.inherent.skyweavecompat.terrain.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.carver.WorldCarver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(WorldCarver.class)
public abstract class CarverLavaMixin {
    @WrapOperation(method = "getCarveState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/VerticalAnchor;resolveY(Lnet/minecraft/world/level/levelgen/WorldGenerationContext;)I"))
    private int lavaHeight(VerticalAnchor anchor, WorldGenerationContext context, Operation<Integer> original,
            @Local(argsOnly = true) BlockPos position) {
        var geology = context instanceof GeologicalCarving scoped ? scoped.skyweave$geology() : null;
        if (geology == null) return original.call(anchor, context);
        return geology.pointHeight(original.call(anchor, geology.reference()), position, GenerationProfile.MIN_Y - 1);
    }
}
