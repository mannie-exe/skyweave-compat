package design.inherent.skyweavecompat.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import design.inherent.skyweavecompat.climate.SurfaceClimateScope;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.RandomState;
import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;

/** Include downstream dressing callbacks while retaining the actual native generation stage. */
@Mixin(value = SkyIslandChunkGenerator.class, remap = false)
public abstract class GenerationClimateMixin {
    @WrapMethod(method = "buildSurface")
    private void surface(WorldGenRegion level, StructureManager structures, RandomState random, ChunkAccess chunk,
            Operation<Void> original) {
        SurfaceClimateScope.run(level,()->original.call(level,structures,random,chunk));
    }
    @WrapMethod(method = "applyCarvers")
    private void carvers(WorldGenRegion level, long seed, RandomState random, BiomeManager biomes,
            StructureManager structures, ChunkAccess chunk, GenerationStep.Carving stage, Operation<Void> original) {
        SurfaceClimateScope.run(level,()->original.call(level,seed,random,biomes,structures,chunk,stage));
    }
}
