package design.inherent.skyweavecompat.mixin.client;

import com.copycatsplus.copycats.foundation.copycat.model.CopycatModelCore.ModelEntry;
import com.copycatsplus.copycats.foundation.copycat.model.assembly.neoforge.CopycatRenderContextNeoForge.CopycatBakedQuad;
import com.copycatsplus.copycats.foundation.copycat.model.neoforge.CopycatModelNeoForge;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import design.inherent.skyweavecompat.client.CopycatsGlass;
import design.inherent.skyweavecompat.client.GlassMesh;
import design.inherent.skyweavecompat.client.GlassSurfaceContacts;
import design.inherent.skyweavecompat.client.TransparencyProfiles;
import design.inherent.skyweavecompat.rendering.GeometryScope;
import design.inherent.skyweavecompat.rendering.TransparencyProfile.Provider;
import java.util.List;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = CopycatModelNeoForge.class, remap = false)
abstract class CopycatsBackfaceMixin implements GlassMesh.Provider {
    @Unique private final TransparencyProfiles.Snapshot skyweave$profiles = TransparencyProfiles.current();
    @Override public long skyweave$resourceGeneration() { return skyweave$profiles.generation(); }
    @Shadow protected abstract List<CopycatBakedQuad> getQuads(BlockState state, RandomSource random, ModelData data, RenderType layer);

    @ModifyReturnValue(method = "getCroppedQuads", at = @At("RETURN"), require = 1, allow = 2)
    private List<CopycatBakedQuad> skyweave$part(List<CopycatBakedQuad> quads, ModelEntry entry, BlockState state,
            List<CopycatBakedQuad> source, BlockState material) {
        return CopycatsGlass.tag(quads, entry, material, skyweave$profiles);
    }

    @WrapMethod(method = "getQuads(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/util/RandomSource;Lnet/neoforged/neoforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)Ljava/util/List;")
    private List<CopycatBakedQuad> skyweave$assembled(BlockState state, RandomSource random, ModelData data,
            RenderType layer, Operation<List<CopycatBakedQuad>> original) {
        if (state == null) return original.call(state, random, data, layer);
        boolean sampling = GeometryScope.sampling(); List<CopycatBakedQuad> quads;
        try (var scope = GeometryScope.source()) { quads = original.call(state, random, data, layer); }
        return sampling ? quads : CopycatsGlass.finish(quads, layer, GlassSurfaceContacts.from(data, skyweave$profiles));
    }

    @WrapMethod(method = "gatherModelData")
    private ModelData.Builder skyweave$data(ModelData.Builder builder, BlockAndTintGetter view, BlockPos pos,
            BlockState state, ModelData incoming, Operation<ModelData.Builder> original) {
        ModelData.Builder result;
        try (var scope = GeometryScope.source()) { result = original.call(builder, view, pos, state, incoming); }
        // Single-material BEs deliver MATERIAL; native gathering expands it into MATERIALS.
        if (!GeometryScope.sampling() && CopycatModelNeoForge.getMaterials(result.build()).values().stream()
                .anyMatch(material -> skyweave$profiles.supports(Provider.MATERIAL, material))) {
            var contacts = GlassSurfaceContacts.capture(view, pos, skyweave$profiles);
            if (contacts != null) result.with(GlassSurfaceContacts.PROPERTY, contacts);
        }
        return result;
    }

    @Override public List<GlassMesh.Face> skyweave$originalFaces(BlockState state, long seed, ModelData data, RenderType layer) {
        try (var scope = GeometryScope.source()) { return CopycatsGlass.faces(getQuads(state, RandomSource.create(seed), data, layer), layer); }
    }
}
