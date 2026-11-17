package design.inherent.skyweavecompat.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.hmioutline.render.MoldRenderer", remap = false)
public interface OutlineMoldAccess {
    @Accessor("renderingHand") static boolean skyweave$hand() { throw new AssertionError(); }
    @Accessor("renderingHand") static void skyweave$hand(boolean value) { throw new AssertionError(); }
}
