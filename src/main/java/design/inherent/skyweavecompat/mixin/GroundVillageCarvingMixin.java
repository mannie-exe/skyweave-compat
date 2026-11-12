package design.inherent.skyweavecompat.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import design.inherent.skyweavecompat.terrain.structure.GroundVillageCarving;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.*;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(NoiseBasedChunkGenerator.class)
public abstract class GroundVillageCarvingMixin {
    @WrapMethod(method="applyCarvers")
    private void skyweave$shallowAnchors(WorldGenRegion level,long seed,RandomState random,BiomeManager biomes,
            StructureManager structures,ChunkAccess chunk,GenerationStep.Carving step,Operation<Void> original) {
        GroundVillageCarving.run((ChunkGenerator)(Object)this,level,chunk,
                ()->original.call(level,seed,random,biomes,structures,chunk,step));
    }
}
