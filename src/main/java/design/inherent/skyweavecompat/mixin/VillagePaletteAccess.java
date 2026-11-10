package design.inherent.skyweavecompat.mixin;

import java.util.List;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(StructureTemplate.class)
public interface VillagePaletteAccess {
    @Accessor("palettes") List<StructureTemplate.Palette> skyweave$palettes();
}
