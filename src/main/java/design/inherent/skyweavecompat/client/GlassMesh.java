package design.inherent.skyweavecompat.client;

import java.util.List;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Final provider geometry and its real material/part/layer ownership, before Compat additions. */
public final class GlassMesh {
    private GlassMesh() {}
    public record Owner(String provider, String part, BlockState material, RenderType layer) {}
    public record Face(BakedQuad quad, Direction cullFace, Owner owner, boolean supported) {}

    /** Implemented only by optional providers with a qualified block-entity ModelData contract. */
    public interface Generation { long skyweave$resourceGeneration(); }
    public interface Provider extends Generation {
        List<Face> skyweave$originalFaces(BlockState state, long seed, ModelData data, RenderType layer);
    }
}
