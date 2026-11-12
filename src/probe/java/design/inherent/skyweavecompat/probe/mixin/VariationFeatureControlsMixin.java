package design.inherent.skyweavecompat.probe.mixin;

import design.inherent.skyweavecompat.probe.VariationProbe;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=SkyIslandChunkGenerator.class,remap=false)
abstract class VariationFeatureControlsMixin {
    @Inject(method="applyBiomeDecoration",at=@At("TAIL"))
    private void skyweave$generationControls(WorldGenLevel level,ChunkAccess chunk,StructureManager structures,CallbackInfo callback) {
        if(Boolean.getBoolean("neighbors.probe.variation") && System.getProperty("neighbors.probe.variationKind","").equals("DEEP_DARK"))
            VariationProbe.generationControls(level,(SkyIslandChunkGenerator)(Object)this,chunk);
    }
}
