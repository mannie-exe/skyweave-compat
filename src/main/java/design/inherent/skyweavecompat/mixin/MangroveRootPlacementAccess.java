package design.inherent.skyweavecompat.mixin;

import net.minecraft.world.level.levelgen.feature.rootplacers.MangroveRootPlacement;
import net.minecraft.world.level.levelgen.feature.rootplacers.MangroveRootPlacer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read the actual configured native root limits, never the stale public default constants. */
@Mixin(MangroveRootPlacer.class)
public interface MangroveRootPlacementAccess {
    @Accessor("mangroveRootPlacement") MangroveRootPlacement skyweave$rootPlacement();
}
