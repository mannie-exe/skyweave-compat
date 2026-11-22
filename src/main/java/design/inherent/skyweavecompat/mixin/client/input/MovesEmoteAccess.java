package design.inherent.skyweavecompat.mixin.client.input;

import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "punchymoves.client.emote.EmoteKeybindManager$RegisteredEmote", remap = false)
public interface MovesEmoteAccess {
    @Accessor("mapping") KeyMapping skyweave$mapping();
}
