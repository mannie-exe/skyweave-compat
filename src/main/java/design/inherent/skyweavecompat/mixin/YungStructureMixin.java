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
    @Override public int skyweave$assemblyDepth() { return maxDepth; }
    @Override public int skyweave$horizontalReach() { return maxDistanceFromCenter; }
}
