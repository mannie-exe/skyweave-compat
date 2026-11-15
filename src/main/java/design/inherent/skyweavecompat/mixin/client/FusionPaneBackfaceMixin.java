package design.inherent.skyweavecompat.mixin.client;

import design.inherent.skyweavecompat.client.GlassBackfaces;
import design.inherent.skyweavecompat.client.PlainGlassModels;
import net.minecraft.client.resources.model.ModelBakery;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fusion applies pane culling after NeoForge's baking event; wrap its finished model. */
@Mixin(targets = "com.supermartijn642.fusion.model.modifiers.block.BlockModelModifierReloadListener", remap = false)
abstract class FusionPaneBackfaceMixin {
    @Inject(method = "applyModelModifiers(Lnet/minecraft/client/resources/model/ModelBakery;)V",
            at = @At("RETURN"), require = 1)
    private void skyweave$finishedPanes(ModelBakery bakery, CallbackInfo callback) {
        PlainGlassModels.wrap(bakery.getBakedTopLevelModels(), id -> GlassBackfaces.vanillaPane(id)
                && design.inherent.skyweavecompat.client.TransparencyProfiles.current().wraps(id));
    }
}
