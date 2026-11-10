package design.inherent.skyweavecompat.mixin;

import design.inherent.skyweavecompat.terrain.structure.PhysicalStructureIdentity;
import design.inherent.skyweavecompat.terrain.structure.SurveyorLayout;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Pseudo
@Mixin(targets = "folk.sisby.surveyor.structure.StructureStartSummary", remap = false)
public abstract class SurveyorLayoutMixin implements SurveyorLayout {
    @Shadow public abstract List<StructurePiece> getChildren();
    @Unique private ResourceLocation skyweave$type;
    @Unique private String skyweave$identity;

    @Override public synchronized String skyweave$identity(ResourceLocation id) {
        if (!id.equals(skyweave$type)) {
            skyweave$identity = PhysicalStructureIdentity.of(id, getChildren().stream()
                    .map(piece -> ((SurveyorPieceAccess)piece).skyweave$pieceTag()).toList());
            skyweave$type = id;
        }
        return skyweave$identity;
    }
}
