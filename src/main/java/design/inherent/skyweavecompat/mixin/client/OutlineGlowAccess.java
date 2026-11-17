package design.inherent.skyweavecompat.mixin.client;

import com.mojang.blaze3d.pipeline.TextureTarget;
import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "net.hmioutline.render.GlowHalo", remap = false)
public interface OutlineGlowAccess {
    @Accessor("DOWN_SHADER") static ShaderInstance skyweave$down() { throw new AssertionError(); }
    @Accessor("BLUR_SHADER") static ShaderInstance skyweave$blur() { throw new AssertionError(); }
    @Accessor("COMPOSITE_SHADER") static ShaderInstance skyweave$composite() { throw new AssertionError(); }
    @Accessor("mask") static TextureTarget skyweave$mask() { throw new AssertionError(); }
    @Accessor("captured") static boolean skyweave$captured() { throw new AssertionError(); }
    @Accessor("captured") static void skyweave$captured(boolean value) { throw new AssertionError(); }
    @Invoker("beginHand") static void skyweave$begin() { throw new AssertionError(); }
    @Invoker("endHand") static void skyweave$end() { throw new AssertionError(); }
}
