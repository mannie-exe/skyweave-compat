package design.inherent.skyweavecompat.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.simibubi.create.content.decoration.copycat.CopycatModel;
import design.inherent.skyweavecompat.client.GlassBackfaces;
import design.inherent.skyweavecompat.client.GlassMesh;
import design.inherent.skyweavecompat.client.GlassSurfaceContacts;
import design.inherent.skyweavecompat.client.TransparencyProfiles;
import design.inherent.skyweavecompat.rendering.GeometryScope;
import design.inherent.skyweavecompat.rendering.TransparencyProfile.Provider;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/** Material processing stays native; only our additions move after the final crop/always-render pass. */
@Mixin(value = CopycatModel.class, remap = false)
abstract class CreateCopycatBackfaceMixin implements GlassMesh.Provider {
    @Unique private final TransparencyProfiles.Snapshot skyweave$profiles = TransparencyProfiles.current();
    @Override public long skyweave$resourceGeneration() { return skyweave$profiles.generation(); }
    @Shadow public abstract List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random, ModelData data, RenderType layer);

    @WrapMethod(method = "getQuads(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;Lnet/minecraft/util/RandomSource;Lnet/neoforged/neoforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)Ljava/util/List;")
    private List<BakedQuad> skyweave$assembled(BlockState state, Direction side, RandomSource random, ModelData data,
            RenderType layer, Operation<List<BakedQuad>> original) {
        if (state == null) return original.call(state, side, random, data, layer);
        boolean sampling = GeometryScope.sampling();
        List<BakedQuad> quads;
        try (var scope = GeometryScope.source()) { quads = original.call(state, side, random, data, layer); }
        var material = CopycatModel.getMaterial(data);
        if (sampling || !skyweave$profiles.supports(Provider.MATERIAL, material)) return quads;
        var owner = new GlassMesh.Owner("create", "material", material, layer);
        var faces = quads.stream().map(q -> new GlassMesh.Face(q, side, owner, true)).toList();
        var output = GlassBackfaces.appendMesh(faces, GlassSurfaceContacts.from(data, skyweave$profiles));
        return output == faces ? quads : output.stream().map(GlassMesh.Face::quad).toList();
    }

    @WrapMethod(method = "gatherModelData")
    private ModelData.Builder skyweave$data(ModelData.Builder builder, BlockAndTintGetter view, BlockPos pos,
            BlockState state, ModelData incoming, Operation<ModelData.Builder> original) {
        ModelData.Builder result;
        try (var scope = GeometryScope.source()) { result = original.call(builder, view, pos, state, incoming); }
        if (!GeometryScope.sampling() && skyweave$profiles.supports(Provider.MATERIAL, CopycatModel.getMaterial(incoming))) {
            var contacts = GlassSurfaceContacts.capture(view, pos, skyweave$profiles);
            if (contacts != null) result.with(GlassSurfaceContacts.PROPERTY, contacts);
        }
        return result;
    }

    @Override public List<GlassMesh.Face> skyweave$originalFaces(BlockState state, long seed, ModelData data, RenderType layer) {
        try (var scope = GeometryScope.source()) {
            var out = new ArrayList<GlassMesh.Face>();
            var random = RandomSource.create(seed);
            var owner = new GlassMesh.Owner("create", "material", CopycatModel.getMaterial(data), layer);
            for (int i = -1; i < 6; i++) {
                Direction cull = i < 0 ? null : Direction.values()[i];
                random.setSeed(seed);
                for (var quad : getQuads(state, cull, random, data, layer)) out.add(new GlassMesh.Face(quad, cull, owner, false));
            }
            return out;
        }
    }
}
