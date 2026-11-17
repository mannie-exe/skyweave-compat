package design.inherent.skyweavecompat.mixin.client;

import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.hmioutline.render.OutlineLayers", remap = false)
public interface OutlineLayersAccess {
    @Accessor("OUTLINE_SHADER") static ShaderInstance skyweave$outline() { throw new AssertionError(); }
    @Accessor("RUNE_SHADER") static ShaderInstance skyweave$rune() { throw new AssertionError(); }
}
