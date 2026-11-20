package design.inherent.skyweavecompat.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import design.inherent.skyweavecompat.terrain.structure.StructureFamily;
import design.inherent.skyweavecompat.terrain.structure.StructurePlacementScope;
import design.inherent.skyweavecompat.terrain.structure.VillageDelegate;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.village.VillagePieceClassifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Let the native classifier inspect the real delegated element, retaining its original decision rules. */
@Mixin(value = VillagePieceClassifier.class, remap = false)
public abstract class VillageClassifierMixin {
    @WrapOperation(method = "classify", at = @At(value = "INVOKE", target = "Ljava/lang/String;valueOf(Ljava/lang/Object;)Ljava/lang/String;"))
    private String skyweave$nativeElement(Object value, Operation<String> original) {
        var scope = StructurePlacementScope.current();
        if (scope != null && scope.family() == StructureFamily.VILLAGES && value instanceof PoolElementStructurePiece piece) {
            var element = piece.getElement();
            for (int depth = 0; element instanceof VillageDelegate delegate && depth < 8; depth++) element = delegate.skyweave$delegate();
            if (element != piece.getElement() && !(element instanceof VillageDelegate)) return original.call(element);
        }
        return original.call(value);
    }
}
