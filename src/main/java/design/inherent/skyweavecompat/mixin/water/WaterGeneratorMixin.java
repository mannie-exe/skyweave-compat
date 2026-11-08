package design.inherent.skyweavecompat.mixin.water;

import design.inherent.skyweavecompat.water.IslandWaterTerrain;
import design.inherent.skyweavecompat.water.WaterField;
import net.minecraft.world.level.levelgen.RandomState;
import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SkyIslandChunkGenerator.class, remap = false)
public abstract class WaterGeneratorMixin {
    @Inject(method = "getIslandField", at = @At("RETURN"))
    private void water(RandomState random, CallbackInfoReturnable<IslandField> result) {
        var generator = (SkyIslandChunkGenerator)(Object)this;
        if (IslandWaterTerrain.applies(generator)) ((WaterField)(Object)result.getReturnValue()).skyweave$water(generator, random);
    }
}
