package design.inherent.skyweavecompat.mixin;

import design.inherent.skyweavecompat.terrain.*;
import org.sathrek.sky_archipelago.config.SkyIslandSettings;
import org.sathrek.sky_archipelago.worldgen.generator.field.BiomeTerrainShaper;
import org.sathrek.sky_archipelago.worldgen.generator.field.TerrainColumn;
import org.sathrek.sky_archipelago.worldgen.generator.field.internal.IslandDensityEvaluator;
import org.sathrek.sky_archipelago.worldgen.generator.field.internal.IslandShapeSampler;
import org.sathrek.sky_archipelago.worldgen.generator.field.model.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Translate native solid remnants; do not replace the native vertical body/taper sampler. */
@Mixin(value = IslandDensityEvaluator.class, remap = false)
public abstract class ShapeDensityMixin {
    @Shadow @Final private IslandShapeSampler shapeSampler;
    @Inject(method = "prepareDensity", at = @At("RETURN"), cancellable = true)
    private void remnantHeight(IslandDescriptor island, HorizontalSample horizontal, int x, int z,
            SkyIslandSettings settings, BiomeTerrainShaper.TerrainProfile biome,
            CallbackInfoReturnable<IslandDensityEvaluator.PreparedDensity> result) {
        var styles = ((StyledTerrain)(Object)shapeSampler).skyweave$styles();
        var prepared = result.getReturnValue();
        if (styles == null || prepared.waterArchetype().exists()) return;
        var fringe = ((FringeColumn)(Object)horizontal).skyweave$fragment();
        if (fringe != null) {
            int top = island.centerY() + island.plateauHeight() + fringe.topOffset();
            result.setReturnValue(new IslandDensityEvaluator.PreparedDensity(prepared.waterArchetype(),
                    new TerrainColumn(top - fringe.depth() + 1, top)));
            return;
        }
        if (!prepared.landBody().exists()) return;
        if (styles.profile().enabled() && styles.profile().fringe().enabled()
                && horizontal.coverage() > .15 && horizontal.coverage() < .7) {
            double point = IslandStyles.noise(island.seed() ^ 0x83baL,
                    horizontal.localX() / 18, horizontal.localZ() / 18);
            int accent = (int)Math.round(Math.max(0, point - .3) / .7 * styles.profile().fringe().undersideAccent());
            var body = prepared.landBody();
            prepared = new IslandDensityEvaluator.PreparedDensity(prepared.waterArchetype(),
                    new TerrainColumn(body.bottomY() - accent, body.topY()));
            result.setReturnValue(prepared);
        }
        if (styles.style(island).kind() == IslandStyles.Kind.NATIVE) return;
        var profile = styles.profile().fractures();
        var fragment = FragmentedExterior.sample(island.seed(), horizontal.localX(), horizontal.localZ(), profile);
        if (!fragment.exists()) {
            result.setReturnValue(new IslandDensityEvaluator.PreparedDensity(prepared.waterArchetype(), TerrainColumn.EMPTY));
            return;
        }
        var generation = ((ProfiledTerrain)(Object)shapeSampler).skyweave$profile();
        int depth = GenerationProfile.depthBelowCenter(island.cliffDepth(), island.plateauHeight(), island.hangDepth());
        int rise = island.plateauHeight() + 48;
        int minimum = Math.max(-profile.verticalRange(), Math.max(
                GenerationProfile.MIN_Y + generation.floorClearance() - island.centerY() + depth,
                depth - island.maxVerticalReach() - 24));
        int maximum = Math.min(profile.verticalRange(), Math.min(
                GenerationProfile.MAX_Y - 1 - generation.buildingHeadroom() - island.centerY() - rise,
                island.maxVerticalReach() + 24 - rise));
        if (minimum > maximum) throw new IllegalStateException("No remnant height fits island " + island.seed());
        int shift = minimum + (int)Math.floor(fragment.heightChoice() * (maximum - minimum + 1));
        var body = prepared.landBody();
        result.setReturnValue(new IslandDensityEvaluator.PreparedDensity(prepared.waterArchetype(),
                new TerrainColumn(body.bottomY() + shift, body.topY() + shift)));
    }
}
