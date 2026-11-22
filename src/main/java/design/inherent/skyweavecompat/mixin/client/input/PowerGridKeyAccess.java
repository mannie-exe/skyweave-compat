package design.inherent.skyweavecompat.mixin.client.input;

import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "org.patryk3211.powergrid.collections.ModdedKeys", remap = false)
public interface PowerGridKeyAccess {
    @Accessor("keybind") KeyMapping skyweave$key();
}
