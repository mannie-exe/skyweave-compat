package design.inherent.skyweavecompat.mixin;

import design.inherent.skyweavecompat.terrain.structure.StructureWorldScope;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.PlacementRequest;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.common.StructureGenerationAttemptService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Sky Archipelago bypasses ChunkGenerator.tryGenerateStructure, where YUNG suppresses vanilla starts. */
@Mixin(value = StructureGenerationAttemptService.class, remap = false)
public abstract class YungReplacementMixin {
    @Inject(method = "generate", at = @At("HEAD"), cancellable = true)
    private void nativeReplacement(ResourceLocation id, Structure structure, PlacementRequest.StructureGenerationContext context,
            long seed, ChunkPos position, ChunkAccess chunk,
            CallbackInfoReturnable<StructureGenerationAttemptService.GenerationAttempt> result) {
        var owner = StructureWorldScope.current();
        if (owner != null && owner.suppresses(structure))
            result.setReturnValue(new StructureGenerationAttemptService.GenerationAttempt(position, StructureStart.INVALID_START,
                    StructureGenerationAttemptService.Outcome.INVALID));
    }
}
