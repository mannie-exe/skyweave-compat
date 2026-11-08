package design.inherent.skyweavecompat.mixin.water;

import com.AdamNew.dynamicwaters.Config;
import com.AdamNew.dynamicwaters.HydrologyManager;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import design.inherent.skyweavecompat.water.IslandWaterFlow;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = HydrologyManager.class, remap = false)
public abstract class WaterOceanFlowMixin {
    @ModifyReturnValue(method = "isOceanBiome", at = @At("RETURN"))
    private static boolean stream(boolean original, Holder<Biome> biome) {
        return original && (!IslandWaterFlow.stream() || Config.isBiomeBlacklisted(biome));
    }
}
