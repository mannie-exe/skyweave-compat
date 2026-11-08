package design.inherent.skyweavecompat.mixin.water;

import com.AdamNew.dynamicwaters.Config;
import com.AdamNew.dynamicwaters.MountainRiverBiome;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import design.inherent.skyweavecompat.climate.IslandClimate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MountainRiverBiome.class, remap = false)
public abstract class WaterBiomeMixin {
    @Inject(method = "wanted", at = @At("HEAD"), cancellable = true)
    private static void islandRivers(WorldGenLevel level, CallbackInfoReturnable<Boolean> result) {
        if (IslandClimate.applies(level))
            result.setReturnValue(Config.MOUNTAIN_RIVER_BIOME.get() && Config.ENABLE_watersheds.get());
    }

    @WrapOperation(method = "paint", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/biome/Biome;coldEnoughToSnow(Lnet/minecraft/core/BlockPos;)Z"))
    private static boolean cold(Biome biome, BlockPos pos, Operation<Boolean> original,
            WorldGenLevel level, ChunkAccess chunk, Holder<Biome> river, Holder<Biome> frozen) {
        return original.call(biome, design.inherent.skyweavecompat.OptionalIntegrations.enabled(
                design.inherent.skyweavecompat.Feature.CLIMATE) ? IslandClimate.temperaturePosition(level, pos) : pos);
    }
}
