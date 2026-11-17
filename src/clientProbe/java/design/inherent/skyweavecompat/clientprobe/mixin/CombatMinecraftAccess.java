package design.inherent.skyweavecompat.clientprobe.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Minecraft.class)
public interface CombatMinecraftAccess {
    @Invoker("startAttack") boolean skyweave$startAttack();
    @Accessor("missTime") void skyweave$missTime(int ticks);
}
