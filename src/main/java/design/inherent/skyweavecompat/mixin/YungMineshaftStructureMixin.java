package design.inherent.skyweavecompat.mixin;

import com.yungnickyoung.minecraft.bettermineshafts.world.BetterMineshaftStructure;
import com.yungnickyoung.minecraft.bettermineshafts.BetterMineshaftsCommon;
import design.inherent.skyweavecompat.terrain.structure.IslandUnderground;
import org.spongepowered.asm.mixin.Mixin;

/** Keep native tunnel assembly while admitting its explicit family tags. */
@Mixin(value = BetterMineshaftStructure.class, remap = false)
public abstract class YungMineshaftStructureMixin implements IslandUnderground {
    @Override public long skyweave$maximumLayoutDisplacement() {
        // 5.1.1 starts at depth -1: five 24-block main tunnels, then small
        // branches restart at zero. Every small child advances depth; its largest
        // horizontal span is eight. Side-room/dungeon branches are terminal.
        // Include the entrance, attachment offsets, terminals and chunk rounding.
        long smallChain = Math.max(0L, BetterMineshaftsCommon.CONFIG.spawnRates.smallShaftPieceChainLength);
        return 24 + 5L * 25 + (smallChain + 1) * 9 + 40 + 32;
    }
}
