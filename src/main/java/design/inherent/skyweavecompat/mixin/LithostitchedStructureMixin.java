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
    @Override public java.util.List<net.minecraft.core.Holder<net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool>>
            skyweave$possibleRoots(net.minecraft.core.RegistryAccess registries) {
        var roots = new java.util.ArrayList<net.minecraft.core.Holder<net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool>>();
        roots.add(config().startPool());
        var pools = registries.registryOrThrow(net.minecraft.core.registries.Registries.TEMPLATE_POOL);
        config().poolAliases().stream().flatMap(binding -> binding.allTargets()).distinct().limit(4096)
                .forEach(key -> roots.add(pools.getHolderOrThrow(key)));
        return java.util.List.copyOf(roots);
    }
    @Override public int skyweave$maximumRootOffset() { return 16; }
}
