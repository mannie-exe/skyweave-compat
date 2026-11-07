package design.inherent.skyweavecompat.mixin;

import org.sathrek.sky_archipelago.worldgen.generator.field.model.ColumnData;
import org.sathrek.sky_archipelago.worldgen.generator.field.model.ResolvedContributor;
import design.inherent.skyweavecompat.terrain.GenerationProfile;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import design.inherent.skyweavecompat.terrain.ProfiledTerrain;
import design.inherent.skyweavecompat.terrain.InteriorResolver;
import design.inherent.skyweavecompat.terrain.IslandInteriors;
import design.inherent.skyweavecompat.terrain.TerrainStage;
import design.inherent.skyweavecompat.terrain.LayeredColumn;
import org.sathrek.sky_archipelago.config.SkyIslandSettings;
import org.sathrek.sky_archipelago.worldgen.generator.field.internal.IslandColumnResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;

@Mixin(value = IslandColumnResolver.class, remap = false)
public abstract class ColumnResolverMixin implements ProfiledTerrain, InteriorResolver {
    @Unique private GenerationProfile skyweave$profile;
    @Override public void skyweave$configure(GenerationProfile profile) { skyweave$profile = profile; }
    @Override public GenerationProfile skyweave$profile() { return skyweave$profile; }
    @Unique private IslandInteriors skyweave$interiors;
    @Override public void skyweave$interiors(IslandInteriors interiors) { skyweave$interiors = interiors; }

    @Inject(method = "resolveColumnDataFromContributors", at = @At("RETURN"), cancellable = true)
    private void interiors(int x, int z, SkyIslandSettings settings, int top, int bottom, long started,
            List<ResolvedContributor> contributors, int checks, boolean snapshot,
            CallbackInfoReturnable<ColumnData> result) {
        result.setReturnValue(skyweave$shaped(checks, skyweave$carve(x, z, settings, contributors, result.getReturnValue())));
    }

    @Inject(method = "resolveTopColumnDataFromContributors", at = @At("RETURN"), cancellable = true)
    private void topInteriors(int x, int z, SkyIslandSettings settings, int top, int bottom, long started,
            List<ResolvedContributor> contributors, int checks, CallbackInfoReturnable<ColumnData> result) {
        result.setReturnValue(skyweave$shaped(checks, skyweave$carve(x, z, settings, contributors, result.getReturnValue())));
    }

    @Unique private ColumnData skyweave$shaped(int checks, ColumnData column) {
        var shape = design.inherent.skyweavecompat.terrain.ShapeConfiguration.profile();
        if (skyweave$profile == null || !shape.enabled() || checks == 0
                || shape.edges().chance() == 0 && shape.fractures().chance() == 0 && shape.fractures().deepDarkChance() == 0)
            return column;
        // A gap can occupy all four lattice corners while a remnant survives between them.
        // Keep candidate-index proven-empty cells fast, but never interpolate possible fractured terrain.
        if (column == ColumnData.EMPTY) column = new ColumnData(column.segments(), column.profile(), column.dominantDescriptor(),
                column.waterArchetype(), column.river(), column.overlayExposedSegments());
        ((LayeredColumn)(Object)column).skyweave$requireExactSampling();
        return column;
    }

    @Unique private ColumnData skyweave$carve(int x, int z, SkyIslandSettings settings,
            List<ResolvedContributor> contributors, ColumnData exterior) {
        if (skyweave$interiors == null || TerrainStage.current() == TerrainStage.EXTERIOR
                || ((LayeredColumn)(Object)exterior).skyweave$hasExterior()) return exterior;
        return skyweave$interiors.carve(x, z, settings, exterior, contributors);
    }

    @WrapOperation(method = "resolveContributorsWithMetrics", at = @At(value = "INVOKE", target =
            "Lorg/sathrek/sky_archipelago/worldgen/generator/field/internal/IslandSearchReach;cellRadius(ILorg/sathrek/sky_archipelago/config/SkyIslandSettings;)I"))
    private int searchRange(int spacing, SkyIslandSettings settings, Operation<Integer> original) {
        return skyweave$profile == null ? original.call(spacing, settings) : skyweave$profile.lookupCells();
    }
}
