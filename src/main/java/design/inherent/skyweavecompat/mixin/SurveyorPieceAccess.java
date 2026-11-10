package design.inherent.skyweavecompat.mixin;

import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

@Pseudo
@Mixin(targets = "folk.sisby.surveyor.structure.StructurePieceSummary", remap = false)
public interface SurveyorPieceAccess {
    @Invoker("toNbt") CompoundTag skyweave$pieceTag();
}
