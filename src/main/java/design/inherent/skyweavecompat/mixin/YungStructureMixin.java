package design.inherent.skyweavecompat.mixin;

import com.yungnickyoung.minecraft.yungsapi.world.structure.YungJigsawStructure;
import design.inherent.skyweavecompat.terrain.structure.IslandJigsaw;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Supplies native layout limits without replacing YUNG's connectors, puzzles or piece placement. */
@Mixin(value = YungJigsawStructure.class, remap = false)
public abstract class YungStructureMixin implements IslandJigsaw {
    @Shadow @Final public int maxDepth;
    @Shadow @Final public int maxDistanceFromCenter;
    @Shadow @Final public net.minecraft.core.Holder<net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool> startPool;
    @Shadow @Final public net.minecraft.util.valueproviders.IntProvider xOffsetInChunk;
    @Shadow @Final public net.minecraft.util.valueproviders.IntProvider zOffsetInChunk;
    @Override public int skyweave$assemblyDepth() { return maxDepth; }
    @Override public int skyweave$horizontalReach() { return maxDistanceFromCenter; }
    @Override public java.util.List<net.minecraft.core.Holder<net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool>>
            skyweave$possibleRoots(net.minecraft.core.RegistryAccess registries) { return java.util.List.of(startPool); }
    @Override public int skyweave$maximumRootOffset() {
        return Math.toIntExact(Math.max(Math.max(Math.abs((long)xOffsetInChunk.getMinValue()), Math.abs((long)xOffsetInChunk.getMaxValue())),
                Math.max(Math.abs((long)zOffsetInChunk.getMinValue()), Math.abs((long)zOffsetInChunk.getMaxValue()))));
    }
}
