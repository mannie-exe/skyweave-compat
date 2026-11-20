package design.inherent.skyweavecompat.mixin.client.definitions;

import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import timmychips.modefiteitemdefinitions.property.type.codec.ItemModelRootDefinition;

@Mixin(targets = "timmychips.modefiteitemdefinitions.property.type.ItemModelTypes$Registry", remap = false)
abstract class DefinitionRegistryMixin {
    @Shadow @Final private static Map<ResourceLocation, ItemModelRootDefinition> rootDefinitions;
    @Inject(method = "clear", at = @At("TAIL"), require = 1, allow = 1)
    private static void skyweave$clearRootMetadata(CallbackInfo ci) { rootDefinitions.clear(); }
}
