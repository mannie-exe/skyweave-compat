package design.inherent.skyweavecompat.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import design.inherent.skyweavecompat.climate.IslandClimate;
import design.inherent.skyweavecompat.terrain.GeologicalGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** The coarse rejection shortcut must use the same climate as the stored shoreline projection. */
@Mixin(targets = "org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator$1", remap = false)
public abstract class ShoreLocatorClimateMixin {
    @Shadow @Final private SkyIslandChunkGenerator this$0;

    @WrapOperation(method = "mightMatchQuart", at = @At(value = "INVOKE",
            target = "Lorg/sathrek/sky_archipelago/worldgen/generator/core/ShoreBiomeLocator;uniformCold(Lnet/minecraft/world/level/biome/Biome;I)Ljava/lang/Boolean;"))
    private Boolean uniform(Biome biome, int y, Operation<Boolean> original) {
        return original.call(biome, ((GeologicalGenerator)(Object)this$0).skyweave$isEnabled() ? 64 : y);
    }

    @WrapOperation(method = {"mightMatchQuart", "mightMatch"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/biome/Biome;coldEnoughToSnow(Lnet/minecraft/core/BlockPos;)Z"), require = 2)
    private boolean cold(Biome biome, BlockPos pos, Operation<Boolean> original) {
        return original.call(biome, ((GeologicalGenerator)(Object)this$0).skyweave$isEnabled()
                ? IslandClimate.temperaturePosition(pos) : pos);
    }
}
