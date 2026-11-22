package design.inherent.skyweavecompat.mixin.client.input;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "org.patryk3211.powergrid.electricity.wire.ClientWireInteractions", remap = false)
public interface PowerGridWireAccess {
    @Accessor("alternatePlacementStatus") static boolean skyweave$alternate() { throw new AssertionError(); }
    @Accessor("alternatePlacementStatus") static void skyweave$alternate(boolean value) { throw new AssertionError(); }
    @Invoker("alternatePlacementCheck") static void skyweave$release(Minecraft mc, int phase) { throw new AssertionError(); }
}
