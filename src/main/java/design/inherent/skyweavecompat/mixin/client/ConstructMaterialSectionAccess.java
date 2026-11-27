package design.inherent.skyweavecompat.mixin.client;

import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Native reset cancels old tasks and compiled admission without closing reusable buffers. */
@Mixin(SectionRenderDispatcher.RenderSection.class)
public interface ConstructMaterialSectionAccess {
    @Invoker("reset") void skyweave$resetMaterial();
}
