package design.inherent.skyweavecompat.mixin;

import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read the native package-private grounding result without reflection or a duplicate grounding algorithm. */
@Mixin(targets = "org.sathrek.sky_archipelago.worldgen.generator.structure.v2.village.VillagePieceGroundingPlanner$GroundingResult", remap = false)
public interface VillageGroundingResultAccess {
    @Accessor("accepted") boolean skyweave$accepted();
    @Accessor("structureStart") StructureStart skyweave$start();
}
