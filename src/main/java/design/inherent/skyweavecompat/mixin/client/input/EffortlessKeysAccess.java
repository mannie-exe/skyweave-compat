package design.inherent.skyweavecompat.mixin.client.input;

import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "neoforge.nl.requios.effortlessbuilding.utilities.KeyBindings", remap = false)
public interface EffortlessKeysAccess {
    @Accessor("openRadialMenu")
    static KeyMapping skyweave$radial() { throw new AssertionError("Native Effortless key accessor was not applied"); }
    @Accessor("undo")
    static KeyMapping skyweave$undo() { throw new AssertionError("Native Effortless undo accessor was not applied"); }
    @Accessor("redo")
    static KeyMapping skyweave$redo() { throw new AssertionError("Native Effortless redo accessor was not applied"); }
}
