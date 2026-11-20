package design.inherent.skyweavecompat.mixin.client.definitions;

import design.inherent.skyweavecompat.client.model.ItemDefinitions;
import net.neoforged.neoforge.client.event.ModelEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "timmychips.modefiteitemdefinitions.neoforge.NeoForgeClientEvents", remap = false)
abstract class DefinitionBindingMixin {
    @Inject(method = "onBakingCompleted", at = @At("TAIL"), require = 1, allow = 1)
    private static void skyweave$publish(ModelEvent.BakingCompleted event, CallbackInfo ci) { ItemDefinitions.bind(event); }
}
