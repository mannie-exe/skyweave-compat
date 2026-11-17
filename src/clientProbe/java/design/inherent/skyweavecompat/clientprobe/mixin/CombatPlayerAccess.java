package design.inherent.skyweavecompat.clientprobe.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
public interface CombatPlayerAccess {
    @Accessor("attackStrengthTicker") void skyweave$attackTicker(int ticks);
}
