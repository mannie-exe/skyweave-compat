package design.inherent.skyweavecompat.mixin;

import design.inherent.skyweavecompat.terrain.*;
import org.sathrek.sky_archipelago.config.SkyIslandSettings;
import org.sathrek.sky_archipelago.worldgen.generator.field.internal.IslandTerrainPlanFactory;
import org.sathrek.sky_archipelago.worldgen.generator.field.model.IslandDescriptor;
import org.sathrek.sky_archipelago.worldgen.generator.field.model.IslandFamily;
import org.sathrek.sky_archipelago.worldgen.generator.field.model.IslandTerrainPlan;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Candidate lookup and clearance reserve the fringe before accepting a cluster. */
@Mixin(value = IslandTerrainPlanFactory.class, remap = false)
public abstract class FringePlanMixin implements ProfiledTerrain {
    @Unique private GenerationProfile skyweave$profile;
    @Override public GenerationProfile skyweave$profile() { return skyweave$profile; }
    @Override public void skyweave$configure(GenerationProfile profile) { skyweave$profile = profile; }

    @Inject(method = "create", at = @At("RETURN"), cancellable = true)
    private void apronBounds(IslandDescriptor descriptor, SkyIslandSettings settings, CallbackInfoReturnable<IslandTerrainPlan> result) {
        var shape = ShapeConfiguration.profile();
        if (skyweave$profile == null || !shape.enabled() || descriptor.archetype().waterBased()
                || descriptor.family() == IslandFamily.SPIRE) return;
        int reach = shape.fringe().maximumReach(descriptor.maxRadius());
        if (reach == 0) return;
        var p = result.getReturnValue(); var b = p.bounds();
        result.setReturnValue(new IslandTerrainPlan(descriptor, p.dedicatedCrescentAnchor(), p.dedicatedWaterAnchor(),
                p.sinRotation(), p.cosRotation(), p.directionalCos(), p.directionalSin(), p.warpXSeed(), p.warpZSeed(),
                p.macroNoiseSeed(), p.ridgeNoiseSeed(), p.coastNoiseSeed(), p.splitNoiseSeed(),
                new IslandTerrainPlan.Bounds(b.minX() - reach, b.minZ() - reach, b.maxX() + reach,
                        b.maxZ() + reach, b.minY(), b.maxY())));
    }
}
