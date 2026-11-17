package design.inherent.skyweavecompat.mixin.client;

import design.inherent.skyweavecompat.client.ConstructCoverage;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Filter before native chunked/single batching and vanilla block-entity dispatch, without changing tracking. */
@Mixin(targets = "dev.ryanhcode.sable.sublevel.render.dispatcher.VanillaSubLevelRenderDispatcher", remap = false)
abstract class ConstructCoverageDrawMixin {
    @ModifyVariable(method = {"renderSectionLayer", "renderAfterSections", "renderBlockEntities"},
            at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 3, allow = 3)
    private Iterable<ClientSubLevel> skyweave$coverage(Iterable<ClientSubLevel> levels) { return ConstructCoverage.filter(levels); }
}
