package design.inherent.skyweavecompat.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import design.inherent.skyweavecompat.terrain.structure.StructurePlacementScope;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.placement.PlacementThrottleCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A bounded, selected opportunity must not disappear because an unrelated previous attempt was expensive. */
@Mixin(targets = "org.sathrek.sky_archipelago.worldgen.generator.structure.StructurePlacementLegacyCore", remap = false)
public abstract class StructurePolicyThrottleMixin {
    @WrapOperation(method = "defaultSurfaceThrottleDirective", at = @At(value = "INVOKE",
            target = "Lorg/sathrek/sky_archipelago/worldgen/generator/structure/v2/common/placement/PlacementThrottleCache;get(Ljava/lang/Object;)Ljava/lang/Object;"))
    private static Object deterministicBudget(PlacementThrottleCache<?, ?> cache, Object key, Operation<Object> original) {
        return StructurePlacementScope.current() == null ? original.call(cache, key) : null;
    }
}
