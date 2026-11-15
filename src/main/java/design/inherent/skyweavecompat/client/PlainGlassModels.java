package design.inherent.skyweavecompat.client;

import design.inherent.skyweavecompat.Feature;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Final-quad wrapper for explicitly admitted static glass models, independent of their mod. */
public final class PlainGlassModels extends BakedModelWrapper<BakedModel> implements GlassMesh.Generation {
    private final TransparencyProfiles.Snapshot profiles = TransparencyProfiles.current();
    public PlainGlassModels(BakedModel original) { super(original); }
    @Override public long skyweave$resourceGeneration() { return profiles.generation(); }

    public static final Set<Feature> FEATURES = Set.of(Feature.CREATE_BACKFACES, Feature.FUSION_BACKFACES,
            Feature.MACAW_GLASS, Feature.CONFECTIONERY_BACKFACES, Feature.BUBBLE_GUM_BACKFACES,
            Feature.ORNATE_GRATE_BACKFACES, Feature.INDUSTRIAL_GRATING_BACKFACES);

    public static void wrap(ModelEvent.ModifyBakingResult event, Predicate<ResourceLocation> target) {
        wrap(event.getModels(), target);
    }

    public static void wrap(Map<ModelResourceLocation, BakedModel> models, Predicate<ResourceLocation> target) {
        models.replaceAll((id, model) -> target.test(id.id())
                && !id.getVariant().equals("inventory") && !(model instanceof PlainGlassModels)
                ? new PlainGlassModels(model) : model);
    }

    @Override
    public ModelData getModelData(BlockAndTintGetter view, BlockPos pos, BlockState state, ModelData incoming) {
        ModelData data;
        try (var scope = design.inherent.skyweavecompat.rendering.GeometryScope.source()) {
            data = originalModel.getModelData(view, pos, state, incoming);
        }
        if (!profiles.supports(design.inherent.skyweavecompat.rendering.TransparencyProfile.Provider.PLAIN, state)) return data;
        var contacts = GlassSurfaceContacts.capture(view, pos, profiles);
        return contacts == null ? data : data.derive().with(GlassSurfaceContacts.PROPERTY, contacts).build();
    }

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random) {
        return getQuads(state, side, random, ModelData.EMPTY, null);
    }

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random, ModelData data, RenderType layer) {
        var quads = originalModel.getQuads(state, side, random, data, layer);
        return profiles.supports(design.inherent.skyweavecompat.rendering.TransparencyProfile.Provider.PLAIN, state)
                ? GlassBackfaces.append(quads, GlassSurfaceContacts.from(data, profiles)) : quads;
    }
}
