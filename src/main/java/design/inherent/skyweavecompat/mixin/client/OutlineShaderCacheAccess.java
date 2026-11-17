package design.inherent.skyweavecompat.mixin.client;

import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ShaderInstance.class)
public interface OutlineShaderCacheAccess {
    @Accessor("lastAppliedShader") static ShaderInstance skyweave$lastShader() { throw new AssertionError(); }
    @Accessor("lastAppliedShader") static void skyweave$lastShader(ShaderInstance shader) { throw new AssertionError(); }
    @Accessor("lastProgramId") static int skyweave$lastProgram() { throw new AssertionError(); }
    @Accessor("lastProgramId") static void skyweave$lastProgram(int program) { throw new AssertionError(); }
}
