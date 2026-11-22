package design.inherent.skyweavecompat.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import design.inherent.skyweavecompat.placement.VillageAnchors;
import design.inherent.skyweavecompat.placement.VillagePlan;
import design.inherent.skyweavecompat.placement.VillageHeightPolicy;
import design.inherent.skyweavecompat.terrain.GeologicalGenerator;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.heightproviders.UniformHeight;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(JigsawStructure.class)
public abstract class VillageHeightMixin {
    @Unique private static final ResourceLocation SKYWEAVE_VILLAGE = ResourceLocation.parse("skyvillages:skyvillage");
    @Unique private static final HeightProvider SKYWEAVE_BAND = UniformHeight.of(VerticalAnchor.absolute(160), VerticalAnchor.absolute(224));

    @Unique private boolean skyweave$owns(Structure.GenerationContext context) {
        return context.chunkGenerator() instanceof GeologicalGenerator generator && generator.skyweave$isEnabled()
                && SKYWEAVE_VILLAGE.equals(context.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey((Structure)(Object)this));
    }

    @WrapOperation(method = "findGenerationPoint", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/heightproviders/HeightProvider;sample(Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/level/levelgen/WorldGenerationContext;)I"))
    private int villageHeight(HeightProvider provider, RandomSource random, WorldGenerationContext height,
            Operation<Integer> original, @Local(argsOnly = true) Structure.GenerationContext context) {
        if (!skyweave$owns(context)) return original.call(provider, random, height);
        int sampled = VillageHeightPolicy.packBand() ? SKYWEAVE_BAND.sample(random, height) : original.call(provider, random, height);
        var generator = (GeologicalGenerator)context.chunkGenerator();
        var sky = (SkyIslandChunkGenerator)context.chunkGenerator();
        var field = generator.skyweave$field(context.randomState());
        int x = context.chunkPos().getMiddleBlockX(), z = context.chunkPos().getMiddleBlockZ();
        var owner = VillagePlan.owner(x, z, ((VillageAnchors)(Object)field).skyweave$anchorsNear(x, z, sky.resolvedSkyIslandSettings()));
        // Preserve the native random spread, centered on the actual host rather than a world-wide band.
        return owner == null ? sampled : sampled + owner.surfaceY() - 192;
    }

    @ModifyExpressionValue(method = "findGenerationPoint", at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/level/levelgen/structure/structures/JigsawStructure;projectStartToHeightmap:Ljava/util/Optional;"), require = 1)
    private java.util.Optional<Heightmap.Types> projection(java.util.Optional<Heightmap.Types> original,
            @Local(argsOnly = true) Structure.GenerationContext context) {
        return skyweave$owns(context) && VillageHeightPolicy.packBand() ? java.util.Optional.empty() : original;
    }
}
