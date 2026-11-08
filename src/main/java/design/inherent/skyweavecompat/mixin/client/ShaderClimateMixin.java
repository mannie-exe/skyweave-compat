package design.inherent.skyweavecompat.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import design.inherent.skyweavecompat.Feature;
import design.inherent.skyweavecompat.OptionalIntegrations;
import design.inherent.skyweavecompat.climate.IslandClimate;
import net.irisshaders.iris.uniforms.BiomeUniforms;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = BiomeUniforms.class, remap = false)
public abstract class ShaderClimateMixin {
    @WrapOperation(method = "lambda$addBiomeUniforms$2", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/biome/Biome;getPrecipitationAt(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/biome/Biome$Precipitation;"))
    private static Biome.Precipitation precipitation(Biome biome, BlockPos pos, Operation<Biome.Precipitation> original,
            LocalPlayer player) {
        return original.call(biome, OptionalIntegrations.enabled(Feature.CLIMATE)
                ? IslandClimate.temperaturePosition(player.level(), pos) : pos);
    }
}
