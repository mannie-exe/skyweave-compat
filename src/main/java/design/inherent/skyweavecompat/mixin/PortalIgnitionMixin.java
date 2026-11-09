package design.inherent.skyweavecompat.mixin;

import design.inherent.skyweavecompat.access.DimensionAccess;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.FireChargeItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Deny frame activation before item, fire, sound and advancement side effects. */
@Mixin({FlintAndSteelItem.class, FireChargeItem.class})
public abstract class PortalIgnitionMixin {
    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true, require = 1)
    private void skyweave$denyActivation(UseOnContext context, CallbackInfoReturnable<InteractionResult> result) {
        if (DimensionAccess.denyIgnition(context)) result.setReturnValue(InteractionResult.FAIL);
    }
}
