package design.inherent.skyweavecompat.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.yungnickyoung.minecraft.bettermineshafts.world.generator.pieces.VerticalEntrance;
import design.inherent.skyweavecompat.terrain.structure.IslandUnderground;
import design.inherent.skyweavecompat.terrain.structure.StructurePlacementScope;
import java.util.List;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.underground.UndergroundPieceFitEvaluator;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.underground.UndergroundStrategySelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** Fit actual rooms through the native 90% coverage solver; the exit search box is not a room. */
@Mixin(value = UndergroundPieceFitEvaluator.class, remap = false)
public abstract class YungMineshaftFitMixin {
    @WrapOperation(method = "findBestFit", at = @At(value = "INVOKE", target =
            "Lorg/sathrek/sky_archipelago/worldgen/generator/structure/v2/underground/UndergroundPieceFitEvaluator;usesAnchorOnlyFit(Lorg/sathrek/sky_archipelago/worldgen/generator/structure/v2/underground/UndergroundStrategySelector$Strategy;)Z"))
    private boolean tunnelCoverage(UndergroundStrategySelector.Strategy strategy, Operation<Boolean> original,
            @Local(argsOnly = true) StructureStart start) {
        return skyweave$owns(start) ? false : original.call(strategy);
    }
    @WrapOperation(method = "findBestFit", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/levelgen/structure/StructureStart;getPieces()Ljava/util/List;"))
    private List<StructurePiece> rooms(StructureStart start, Operation<List<StructurePiece>> original) {
        var pieces = original.call(start);
        return skyweave$owns(start) ? pieces.stream().filter(piece -> !(piece instanceof VerticalEntrance)).toList() : pieces;
    }
    @Unique private static boolean skyweave$owns(StructureStart start) {
        return StructurePlacementScope.current() != null && start.getStructure() instanceof IslandUnderground;
    }
}
