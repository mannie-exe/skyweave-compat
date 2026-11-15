package design.inherent.skyweavecompat.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import design.inherent.skyweavecompat.client.GlassBackfaces;
import design.inherent.skyweavecompat.client.GlassMesh;
import design.inherent.skyweavecompat.client.GlassSurfaceContacts;
import design.inherent.skyweavecompat.client.TransparencyProfiles;
import design.inherent.skyweavecompat.rendering.TransparencyProfile.Provider;
import java.util.List;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** Reverse the final UV selection, never feed reversed normals into CT lookup. */
@Mixin(targets = "com.simibubi.create.foundation.block.connected.CTModel", remap = false)
abstract class CreateGlassBackfaceMixin implements GlassMesh.Generation {
    @Unique private final TransparencyProfiles.Snapshot skyweave$profiles = TransparencyProfiles.current();
    @Override public long skyweave$resourceGeneration() { return skyweave$profiles.generation(); }

    @ModifyReturnValue(method = "getQuads", at = @At("RETURN"), require = 1, allow = 2)
    private List<BakedQuad> skyweave$backfaces(List<BakedQuad> quads, BlockState state, Direction side,
            RandomSource random, ModelData data, RenderType layer) {
        if (state == null) return quads;
        return skyweave$profiles.supports(Provider.CREATE_CT, state)
                ? GlassBackfaces.append(quads, GlassSurfaceContacts.from(data, skyweave$profiles)) : quads;
    }

    @ModifyReturnValue(method = "gatherModelData", at = @At("RETURN"), require = 1)
    private ModelData.Builder skyweave$contacts(ModelData.Builder result, ModelData.Builder builder,
            BlockAndTintGetter view, BlockPos pos, BlockState state, ModelData original) {
        if (skyweave$profiles.supports(Provider.CREATE_CT, state)) {
            var contacts = GlassSurfaceContacts.capture(view, pos, skyweave$profiles);
            if (contacts != null) result.with(GlassSurfaceContacts.PROPERTY, contacts);
        }
        return result;
    }
}
