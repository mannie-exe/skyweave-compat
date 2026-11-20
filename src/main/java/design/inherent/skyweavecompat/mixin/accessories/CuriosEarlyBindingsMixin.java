package design.inherent.skyweavecompat.mixin.accessories;

import io.wispforest.accessories_compat.curios.wrapper.CuriosConversionUtils;
import java.util.Map;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.theillusivec4.curios.common.data.CuriosEntityManager;

@Mixin(value = CuriosEntityManager.class, remap = false)
public abstract class CuriosEarlyBindingsMixin {
    @Inject(method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
            at = @At("HEAD"), require = 1)
    private void skyweave$discardPreviousBindings(Map<?, ?> resources, ResourceManager manager, ProfilerFiller profiler, CallbackInfo ci) {
        if ((Object) this == CuriosEntityManager.SERVER) CuriosConversionUtils.CURRENT_ENTITY_BINDINGS.clear();
    }
}
