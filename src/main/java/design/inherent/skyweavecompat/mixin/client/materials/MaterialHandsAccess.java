package design.inherent.skyweavecompat.mixin.client.materials;

import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemInHandRenderer.class)
public interface MaterialHandsAccess {
    @Accessor("mainHandItem") ItemStack skyweave$main();
    @Accessor("offHandItem") ItemStack skyweave$off();
}
