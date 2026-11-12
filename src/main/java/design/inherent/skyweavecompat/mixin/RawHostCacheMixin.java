package design.inherent.skyweavecompat.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import design.inherent.skyweavecompat.climate.RawHostClimate;
import design.inherent.skyweavecompat.climate.StableClimateSource;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Reuse the private bounded native index without changing raw depth or the original source tree. */
@Mixin(targets="org.sathrek.sky_archipelago.worldgen.generator.core.HostBiomeCache",remap=false)
public abstract class RawHostCacheMixin {
    @WrapOperation(method="rawSample",at=@At(value="INVOKE",target=
            "Lorg/sathrek/sky_archipelago/worldgen/generator/core/RiverHostBiomeResolver;sampleRaw(Lnet/minecraft/world/level/biome/BiomeSource;IIILnet/minecraft/world/level/biome/Climate$Sampler;)Lnet/minecraft/core/Holder;"))
    private Holder<Biome> skyweave$raw(BiomeSource source,int x,int y,int z,Climate.Sampler sampler,Operation<Holder<Biome>> original) {
        return RawHostClimate.owns(this) && source.getClass()==MultiNoiseBiomeSource.class
                ?((StableClimateSource)source).skyweave$stableBiome(sampler.sample(x,y,z))
                :original.call(source,x,y,z,sampler);
    }
}
