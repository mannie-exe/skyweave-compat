package design.inherent.skyweavecompat.mixin.client;

import com.google.gson.JsonElement;
import design.inherent.skyweavecompat.client.model.MultiAxisModels;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

/** Preserve downloaded model resources while letting the native NeoForge loader bake their geometry. */
@Mixin(targets = "net.neoforged.neoforge.client.model.ExtendedBlockModelDeserializer", remap = false)
public abstract class MultiAxisModelMixin {
    @ModifyVariable(method = "deserialize(Lcom/google/gson/JsonElement;Ljava/lang/reflect/Type;Lcom/google/gson/JsonDeserializationContext;)Lnet/minecraft/client/renderer/block/model/BlockModel;",
            at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 1, allow = 1)
    private JsonElement skyweave$modernElements(JsonElement input) { return MultiAxisModels.adapt(input); }
}
