package design.inherent.skyweavecompat.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.simulated_team.simulated.service.SimConfigService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The server ceiling constrains effective range; it must not rewrite shared COMMON preferences. */
@Mixin(targets = "net.sprocketgames.create_aeronautics_throwable_rope_connector.config.ModCommonConfig", remap = false)
abstract class RopeConnectorRangeMixin {
    @Inject(method = "correctRangeValuesToSimulatedMax", at = @At("HEAD"), cancellable = true)
    private static void skyweave$retainConfiguredRanges(CallbackInfoReturnable<Boolean> result) {
        result.setReturnValue(false);
    }

    @ModifyReturnValue(method = {"getClampedThrowDistance", "getClampedLauncherMaxDistance", "getClampedMountedLauncherRange"},
            at = @At("RETURN"), require = 3)
    private static double skyweave$effectiveRange(double configured) {
        var service = SimConfigService.INSTANCE;
        return service.serverLoaded() ? Math.min(configured, service.server().blocks.maxRopeRange.getF()) : configured;
    }
}
