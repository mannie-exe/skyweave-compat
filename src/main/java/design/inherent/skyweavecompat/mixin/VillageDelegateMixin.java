package design.inherent.skyweavecompat.mixin;

import design.inherent.skyweavecompat.terrain.structure.VillageDelegate;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

@Pseudo
@Mixin(targets = "dev.worldgen.lithostitched.worldgen.poolelement.DelegatingPoolElement", remap = false)
public abstract class VillageDelegateMixin implements VillageDelegate {
    @Shadow public abstract StructurePoolElement delegate();
    @Override public StructurePoolElement skyweave$delegate() { return delegate(); }
}
