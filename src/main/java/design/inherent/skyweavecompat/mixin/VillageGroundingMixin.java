package design.inherent.skyweavecompat.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import design.inherent.skyweavecompat.terrain.structure.VillageLayoutRepair;
import java.util.List;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import org.sathrek.sky_archipelago.config.SkyIslandSettings;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;
import org.sathrek.sky_archipelago.worldgen.generator.structure.v2.village.VillagePieceGroundingPlanner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value=VillagePieceGroundingPlanner.class,remap=false)
public abstract class VillageGroundingMixin {
    @WrapOperation(method="normalize(Lnet/minecraft/world/level/levelgen/structure/StructureStart;Lnet/minecraft/world/level/ChunkPos;Ljava/util/List;Lorg/sathrek/sky_archipelago/worldgen/generator/field/IslandField;Lorg/sathrek/sky_archipelago/config/SkyIslandSettings;)Lorg/sathrek/sky_archipelago/worldgen/generator/structure/v2/village/VillagePieceGroundingPlanner$GroundingResult;",
            at=@At(value="INVOKE",target="Lorg/sathrek/sky_archipelago/worldgen/generator/structure/v2/village/VillagePieceGroundingPlanner;hasNucleus(Ljava/util/List;)Z"))
    private boolean supportedNucleus(VillagePieceGroundingPlanner planner,List<StructurePiece> kept,Operation<Boolean> original,
            @Local(argsOnly=true) IslandField field,@Local(argsOnly=true) SkyIslandSettings settings,
            @Local(argsOnly=true) net.minecraft.world.level.levelgen.structure.StructureStart start) {
        var retained=VillageLayoutRepair.retain(kept,start,field,settings);
        if(retained!=kept) { kept.clear();kept.addAll(retained); }
        return original.call(planner,kept);
    }
}
