package design.inherent.skyweavecompat.mixin.client.input;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractContainerScreen.class)
public interface IpnContainerAccess {
    @Invoker("isHovering") boolean skyweave$hovering(int x, int y, int width, int height, double mouseX, double mouseY);
}
