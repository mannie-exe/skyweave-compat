package design.inherent.skyweavecompat.mixin;

import design.inherent.skyweavecompat.terrain.*;
import org.sathrek.sky_archipelago.worldgen.generator.field.WaterArchetypeColumnProfile;
import org.sathrek.sky_archipelago.worldgen.generator.field.internal.IslandShapeSampler;
import org.sathrek.sky_archipelago.worldgen.generator.field.model.HorizontalSample;
import org.sathrek.sky_archipelago.worldgen.generator.field.model.IslandDescriptor;
import org.sathrek.sky_archipelago.worldgen.generator.field.model.IslandTerrainPlan;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = IslandShapeSampler.class, remap = false)
public abstract class OceanShapeMixin implements ProfiledTerrain, StyledTerrain {
    @Unique private GenerationProfile skyweave$profile;
    @Override public void skyweave$configure(GenerationProfile profile) { skyweave$profile = profile; }
    @Override public GenerationProfile skyweave$profile() { return skyweave$profile; }
    @Unique private IslandStyles skyweave$styles;
    @Override public IslandStyles skyweave$styles() { return skyweave$styles; }
    @Override public void skyweave$styles(IslandStyles styles) { skyweave$styles = styles; }

    @WrapMethod(method = "sampleHorizontal")
    private HorizontalSample shape(IslandTerrainPlan plan, int x, int z, Operation<HorizontalSample> original) {
        var island = plan.descriptor();
        if (skyweave$styles == null || !skyweave$styles.profile().enabled() || island.archetype().waterBased())
            return original.call(plan, x, z);
        var profile = skyweave$styles.profile(); var outline = profile.outline();
        double radius = Math.min(island.radiusX(), island.radiusZ());
        // The native plan already budgets a .14-radius domain-warp envelope.
        double amplitude = Math.min(outline.warpAmplitude()
                * IslandStyles.strengthScale(island.seed(), 0x4fc139L, profile), radius * .1);
        double px = (x - island.centerX()) / (double)outline.warpWavelength();
        double pz = (z - island.centerZ()) / (double)outline.warpWavelength();
        int wx = x + (int)Math.round(IslandStyles.noise(island.seed() ^ 0x391ac7L, px, pz) * amplitude);
        int wz = z + (int)Math.round(IslandStyles.noise(island.seed() ^ 0x618f49L, px, pz) * amplitude);
        var horizontal = original.call(plan, wx, wz);
        if (!horizontal.inInfluence()) return horizontal;
        var style = skyweave$styles.style(island);
        var edges = profile.edges();
        double angle = Math.atan2(horizontal.localZ() / island.radiusZ(), horizontal.localX() / island.radiusX());
        double edgeScale = IslandStyles.strengthScale(island.seed(), 0x3ab97fL, profile);
        double mask = IslandStyles.perimeterMask(island.seed(), angle, Math.min(1, edges.affectedFraction() * edgeScale));
        double band = 1 - IslandStyles.smooth(Math.max(0, horizontal.coverage()) * radius / (2 * edges.bandWidth()));
        double wear = IslandStyles.noise(island.seed() ^ 0x75fb29L,
                horizontal.localX() / edges.wavelength(), horizontal.localZ() / edges.wavelength());
        double coverage = horizontal.coverage();
        if (style.disturbed()) coverage += wear * Math.min(edges.amplitude() * edgeScale,
                Math.min(edges.bandWidth(), radius * .1)) * 2 / radius * mask * band;
        if (style.kind() != IslandStyles.Kind.NATIVE || style.disturbed() && mask * band > .6) {
            if (!FragmentedExterior.sample(island.seed(), horizontal.localX(), horizontal.localZ(), profile.fractures()).exists())
                coverage = -1;
        }
        if (coverage == horizontal.coverage()) return horizontal;
        return new HorizontalSample(horizontal.localX(), horizontal.localZ(), coverage, horizontal.branchCoverage(),
                1 - Math.clamp(coverage, 0, 1), horizontal.attachmentStrength(), coverage > -.28);
    }

    @Inject(method = "sampleWaterArchetypeColumnProfile", at = @At("RETURN"), cancellable = true)
    private void waterEdge(IslandDescriptor island, HorizontalSample horizontal,
            CallbackInfoReturnable<WaterArchetypeColumnProfile> result) {
        if (skyweave$profile != null)
            result.setReturnValue(OceanIslands.adapt(island, horizontal, result.getReturnValue(), OceanConfiguration.profile()));
    }
}
