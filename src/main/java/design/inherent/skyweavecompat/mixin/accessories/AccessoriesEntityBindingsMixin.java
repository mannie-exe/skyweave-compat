package design.inherent.skyweavecompat.mixin.accessories;

import design.inherent.skyweavecompat.accessories.CuriosEntityBindings;
import io.wispforest.accessories.api.slot.SlotType;
import io.wispforest.accessories.data.EntitySlotLoader;
import io.wispforest.accessories.data.SlotTypeLoader;
import io.wispforest.accessories.impl.AccessoriesCapabilityImpl;
import io.wispforest.accessories_compat.api.EntityBindingModifier;
import io.wispforest.accessories_compat.curios.CuriosCompat;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SequencedMap;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EntitySlotLoader.class, remap = false)
public abstract class AccessoriesEntityBindingsMixin implements CuriosEntityBindings {
    @Shadow private SequencedMap<EntityType<?>, SequencedMap<String, SlotType>> server;
    @Unique private SequencedMap<EntityType<?>, SequencedMap<String, SlotType>> skyweave$nativeBindings;

    @Inject(method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
            at = @At("RETURN"), require = 1)
    private void skyweave$rememberNativeBindings(Map<?, ?> resources, ResourceManager manager, ProfilerFiller profiler, CallbackInfo ci) {
        skyweave$nativeBindings = server;
    }

    @Override public void skyweave$publishCuriosBindings() {
        if (skyweave$nativeBindings == null)
            throw new IllegalStateException("Accessories entity data was unavailable at server tag publication");
        var combined = new LinkedHashMap<EntityType<?>, SequencedMap<String, SlotType>>();
        skyweave$nativeBindings.forEach((type, slots) -> combined.put(type, new LinkedHashMap<>(slots)));
        var modifier = EntityBindingModifier.from(SlotTypeLoader.INSTANCE.getSlotTypes(false), combined);
        CuriosCompat.INSTANCE.addEntityBindings(modifier);
        combined.replaceAll((type, slots) -> Collections.unmodifiableSequencedMap(slots));
        server = Collections.unmodifiableSequencedMap(combined);
        AccessoriesCapabilityImpl.clearValidationCache(false);
    }

    @Override public void skyweave$releaseCuriosBindings() {
        skyweave$nativeBindings = null;
    }
}
