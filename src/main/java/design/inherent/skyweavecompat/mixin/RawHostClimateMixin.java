package design.inherent.skyweavecompat.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import design.inherent.skyweavecompat.climate.RawHostClimate;
import design.inherent.skyweavecompat.terrain.GeologicalGenerator;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import org.sathrek.sky_archipelago.config.settings.RiverSettings;
import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

/** Exact pinned cache entry points; nested foreign generator calls explicitly suspend ownership. */
@SuppressWarnings("try")
@Mixin(value=SkyIslandChunkGenerator.class,remap=false)
public abstract class RawHostClimateMixin {
    @WrapOperation(method={"sky_archipelago$resolveLandHostBiome","sky_archipelago$projectedBiome","sky_archipelago$findClosestShoreBiome",
            "lambda$getIslandField$5","lambda$buildSurface$2"},at=@At(value="INVOKE",target=
            "Lorg/sathrek/sky_archipelago/worldgen/generator/core/HostBiomeCache;resolve(Lnet/minecraft/world/level/biome/BiomeSource;IIILnet/minecraft/world/level/biome/Climate$Sampler;Lorg/sathrek/sky_archipelago/config/settings/RiverSettings;Z)Lnet/minecraft/core/Holder;"),require=5)
    private Holder<Biome> skyweave$host(@Coerce Object cache,BiomeSource source,int x,int y,int z,Climate.Sampler sampler,
            RiverSettings rivers,boolean shores,Operation<Holder<Biome>> original) {
        try(var ignored=RawHostClimate.query(((GeologicalGenerator)this).skyweave$isEnabled()?cache:null)) {
            return original.call(cache,source,x,y,z,sampler,rivers,shores);
        }
    }
}
