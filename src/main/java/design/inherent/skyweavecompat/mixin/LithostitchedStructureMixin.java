package design.inherent.skyweavecompat.mixin;

import design.inherent.skyweavecompat.terrain.structure.IslandJigsaw;
import dev.worldgen.lithostitched.worldgen.structure.AlternateJigsawConfig;
import dev.worldgen.lithostitched.worldgen.structure.AlternateJigsawStructure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Exposes pinned native metadata; assembly, aliases, processors and templates remain upstream-owned. */
@Mixin(value = AlternateJigsawStructure.class, remap = false)
public abstract class LithostitchedStructureMixin implements IslandJigsaw {
    @Shadow public abstract AlternateJigsawConfig config();
    @Override public int skyweave$assemblyDepth() { return config().size().getMaxValue(); }
    @Override public int skyweave$horizontalReach() { return config().maxDistanceFromCenter().horizontal(); }
}
