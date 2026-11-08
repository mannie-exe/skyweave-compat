package design.inherent.skyweavecompat.mixin;

import com.yungnickyoung.minecraft.bettermineshafts.world.BetterMineshaftStructure;
import design.inherent.skyweavecompat.terrain.structure.IslandUnderground;
import org.spongepowered.asm.mixin.Mixin;

/** Keep native tunnel assembly while admitting its explicit family tags. */
@Mixin(value = BetterMineshaftStructure.class, remap = false)
public abstract class YungMineshaftStructureMixin implements IslandUnderground {}
