package design.inherent.skyweavecompat.mixin.client.definitions;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import design.inherent.skyweavecompat.client.model.*;
import java.util.Set;
import net.minecraft.client.resources.model.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ModelBakery.class)
abstract class DefinitionBakeryMixin implements DefinitionBake {
    @Unique private ItemDefinitions.Prepared skyweave$prepared;
    @WrapOperation(method = "<init>", at = @At(value = "INVOKE", target =
            "Lnet/neoforged/neoforge/client/ClientHooks;onRegisterAdditionalModels(Ljava/util/Set;)V"), require = 1, allow = 1)
    private void skyweave$collect(Set<ModelResourceLocation> models, Operation<Void> original) {
        try { original.call(models); }
        finally { skyweave$prepared = ItemDefinitions.takePreparation(); }
    }
    @Override public ItemDefinitions.Prepared skyweave$definitions() { return skyweave$prepared; }
}
