package design.inherent.skyweavecompat.mixin;

import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasBinding;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Native limits and possible roots, without assembling a layout or querying terrain. */
@Mixin(JigsawStructure.class)
public interface JigsawSearchAccess {
    @Accessor("startPool") Holder<StructureTemplatePool> skyweave$startPool();
    @Accessor("poolAliases") List<PoolAliasBinding> skyweave$poolAliases();
    @Accessor("maxDistanceFromCenter") int skyweave$maximumDistance();
}
