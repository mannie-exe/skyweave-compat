package design.inherent.skyweavecompat.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import design.inherent.skyweavecompat.climate.IslandClimate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerLevel.class)
public abstract class ServerClimateMixin {
    @WrapOperation(method = "tickPrecipitation", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/biome/Biome;shouldFreeze(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Z"))
    private boolean waterBiome(Biome air, net.minecraft.world.level.LevelReader level, BlockPos water,
            Operation<Boolean> original) {
        return original.call(IslandClimate.applies(level) ? level.getBiome(water).value() : air, level, water);
    }

    @WrapOperation(method = "tickPrecipitation", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/biome/Biome;getPrecipitationAt(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/biome/Biome$Precipitation;"))
    private Biome.Precipitation precipitation(Biome biome, BlockPos position, Operation<Biome.Precipitation> original) {
        return original.call(biome, IslandClimate.temperaturePosition((ServerLevel)(Object)this, position));
    }
}
