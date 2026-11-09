package design.inherent.skyweavecompat.mixin;

import design.inherent.skyweavecompat.access.DimensionAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.portal.PortalShape;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Backstop native shape creation, including environmental ignition. */
@Mixin(PortalShape.class)
public abstract class PortalCreationMixin {
    @Shadow @Final private LevelAccessor level;
    @Inject(method = "createPortalBlocks", at = @At("HEAD"), cancellable = true, require = 1)
    private void skyweave$inertFrame(CallbackInfo result) {
        if (level instanceof ServerLevel server && DimensionAccess.active(server.getServer())) result.cancel();
    }
}
