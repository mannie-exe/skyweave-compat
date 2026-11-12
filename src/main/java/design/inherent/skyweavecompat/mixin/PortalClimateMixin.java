package design.inherent.skyweavecompat.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import design.inherent.skyweavecompat.climate.IslandClimate;
import design.inherent.skyweavecompat.terrain.GeologicalGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.structures.RuinedPortalStructure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(RuinedPortalStructure.class)
public abstract class PortalClimateMixin {
    @WrapOperation(method = "lambda$findGenerationPoint$2", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/structure/structures/RuinedPortalStructure;isCold(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Holder;)Z"))
    private static boolean portalTemperature(BlockPos position, Holder<Biome> biome, Operation<Boolean> original,
            @Local(argsOnly = true) Structure.GenerationContext context) {
        return original.call(context.chunkGenerator() instanceof org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator generator
                ? design.inherent.skyweavecompat.climate.ClimateRuntime.temperaturePosition(generator,context.randomState(),position)
                : position, biome);
    }
}
