package design.inherent.skyweavecompat.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import design.inherent.skyweavecompat.climate.IslandClimate;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(FogRenderer.class)
public abstract class IslandVoidFogMixin {
    // Replace only the height baseline, before native blindness/darkness modifies it.
    @ModifyVariable(method = "setupColor", name = "f5", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/FogRenderer;getPriorityFogFunction(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/FogRenderer$MobEffectFogFunction;"))
    private static float baseline(float original, @Local(argsOnly = true) ClientLevel level) {
        return IslandClimate.applies(level) ? 1 : original;
    }
}
