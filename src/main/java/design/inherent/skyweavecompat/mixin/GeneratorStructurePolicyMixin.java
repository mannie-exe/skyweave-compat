package design.inherent.skyweavecompat.mixin;

import design.inherent.skyweavecompat.terrain.GeologicalGenerator;
import design.inherent.skyweavecompat.terrain.structure.StructurePlacementState;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkGenerator.class)
public abstract class GeneratorStructurePolicyMixin {
    @Inject(method = "createState", at = @At("RETURN"))
    private void configureStructures(HolderLookup<StructureSet> sets, RandomState random, long seed,
            CallbackInfoReturnable<ChunkGeneratorStructureState> result) {
        if ((Object)this instanceof SkyIslandChunkGenerator sky && ((GeologicalGenerator)(Object)sky).skyweave$isEnabled())
            ((StructurePlacementState)result.getReturnValue()).skyweave$configureStructures(sky);
    }
}
