package design.inherent.skyweavecompat.mixin;

import com.mojang.datafixers.util.Pair;
import java.util.List;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Inspect distinct entries, not a potentially enormous expanded weighted list. */
@Mixin(StructureTemplatePool.class)
public interface TemplatePoolSearchAccess {
    @Accessor("rawTemplates") List<Pair<StructurePoolElement, Integer>> skyweave$entries();
}
