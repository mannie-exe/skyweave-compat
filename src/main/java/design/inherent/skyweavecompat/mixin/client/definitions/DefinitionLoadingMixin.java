package design.inherent.skyweavecompat.mixin.client.definitions;

import design.inherent.skyweavecompat.client.model.ItemDefinitions;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "timmychips.modefiteitemdefinitions.ModefiteClientCommon", remap = false)
abstract class DefinitionLoadingMixin {
    @Inject(method = "loadItemDefinitions", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private static void skyweave$prepare(ResourceManager resources, CallbackInfo ci) {
        ItemDefinitions.prepare(resources); ci.cancel();
    }
}
