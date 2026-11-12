package design.inherent.skyweavecompat.mixin;

import net.minecraft.world.level.levelgen.Beardifier;
import org.sathrek.sky_archipelago.worldgen.generator.field.TerrainColumn;
import org.sathrek.sky_archipelago.worldgen.generator.terrain.StructureTerrainAdaptation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Reuse the pinned column-density composition rather than copying its implementation. */
@Mixin(value=StructureTerrainAdaptation.class,remap=false)
public interface StructureBeardifierAccess {
    @Invoker("adjustedTop")
    static int skyweave$adjustedTop(Beardifier kernel,int x,int z,TerrainColumn natural) { throw new AssertionError(); }
}
