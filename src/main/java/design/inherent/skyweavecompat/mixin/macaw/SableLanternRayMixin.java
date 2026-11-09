package design.inherent.skyweavecompat.mixin.macaw;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import design.inherent.skyweavecompat.macaw.LanternSelection;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Injection must outrank Sable's priority-1100 overwrite; preparation sees its merged helper. */
@Mixin(value = BlockGetter.class, priority = 1200)
public interface SableLanternRayMixin {
    @WrapOperation(method = "clip", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/BlockGetter;originalClip(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/world/level/ClipContext;)Lnet/minecraft/world/phys/BlockHitResult;",
            remap = false), require = 3, allow = 3)
    private BlockHitResult skyweave$selectInFrame(BlockGetter getter, ClipContext context, Operation<BlockHitResult> original) {
        return LanternSelection.extend(getter, context, original.call(getter, context));
    }
}
