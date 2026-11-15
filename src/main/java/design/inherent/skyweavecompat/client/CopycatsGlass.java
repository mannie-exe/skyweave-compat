package design.inherent.skyweavecompat.client;

import com.copycatsplus.copycats.foundation.copycat.model.CopycatModelCore.ModelEntry;
import com.copycatsplus.copycats.foundation.copycat.model.assembly.neoforge.CopycatRenderContextNeoForge.CopycatBakedQuad;
import design.inherent.skyweavecompat.rendering.TransparencyProfile.Provider;
import java.util.List;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.block.state.BlockState;

/** Copycats-only boundary; never linked by a common, server or absent-provider path. */
public final class CopycatsGlass {
    private CopycatsGlass() {}
    private static final class Original extends CopycatBakedQuad {
        private final BlockState material;
        Original(CopycatBakedQuad quad, BlockState material) {
            super(quad, quad.cullFace, quad.property); this.material = material;
        }
    }
    public static List<CopycatBakedQuad> tag(List<CopycatBakedQuad> quads, ModelEntry entry, BlockState material,
            TransparencyProfiles.Snapshot profiles) {
        if (!entry.type().useCopycatLogic() || !profiles.supports(Provider.MATERIAL, material)) return quads;
        return quads.stream().map(q -> (CopycatBakedQuad) new Original(q, material)).toList();
    }
    public static List<GlassMesh.Face> faces(List<CopycatBakedQuad> quads, RenderType layer) {
        return quads.stream().map(q -> new GlassMesh.Face(q, q.cullFace,
                new GlassMesh.Owner("copycats", q.property, q instanceof Original original ? original.material : null, layer),
                q instanceof Original)).toList();
    }
    public static List<CopycatBakedQuad> finish(List<CopycatBakedQuad> quads, RenderType layer, GlassSurfaceContacts contacts) {
        boolean qualified = false;
        for (var quad : quads) if (quad instanceof Original) { qualified = true; break; }
        if (!qualified) return quads;
        var faces = faces(quads, layer); var result = GlassBackfaces.appendMesh(faces, contacts);
        if (faces == result) return quads;
        return result.stream().map(f -> f.quad() instanceof CopycatBakedQuad q ? q
                : new CopycatBakedQuad(f.quad(), f.cullFace(), f.owner().part())).toList();
    }
}
