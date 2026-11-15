package design.inherent.skyweavecompat.mixin.client;

import com.copycatsplus.copycats.foundation.copycat.model.neoforge.CopycatModelNeoForge.Renderer;
import com.copycatsplus.copycats.foundation.copycat.model.assembly.neoforge.CopycatRenderContextNeoForge.CopycatBakedQuad;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import design.inherent.skyweavecompat.rendering.GeometryScope;
import java.util.List;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** A source read neither consumes nor replaces the native session's finalized render cache. */
@Mixin(targets = "com.copycatsplus.copycats.foundation.copycat.model.neoforge.CopycatModelNeoForge$RenderSession", remap = false)
abstract class CopycatsRenderSessionMixin {
    @Shadow @Final private Renderer renderer;
    @WrapMethod(method = "getQuads")
    private List<CopycatBakedQuad> skyweave$phase(BlockState state, RandomSource random, ModelData data, RenderType layer,
            Operation<List<CopycatBakedQuad>> original) {
        return GeometryScope.sampling() ? renderer.getQuads(state, random, data, layer) : original.call(state, random, data, layer);
    }
}
