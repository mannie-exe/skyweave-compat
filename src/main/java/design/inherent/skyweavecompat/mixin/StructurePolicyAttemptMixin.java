package design.inherent.skyweavecompat.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import design.inherent.skyweavecompat.terrain.structure.StructurePlacementScope;
import design.inherent.skyweavecompat.terrain.structure.StructureWorldScope;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import org.sathrek.sky_archipelago.worldgen.generator.structure.StructurePlacementPassContext;
import org.sathrek.sky_archipelago.worldgen.generator.structure.StructurePlacementPipeline;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = StructurePlacementPipeline.class, remap = false)
public abstract class StructurePolicyAttemptMixin {
    @WrapMethod(method = "createStructures")
    private void bindPlanning(StructurePlacementPassContext pass, Operation<Void> original) {
        var distribution = ((design.inherent.skyweavecompat.terrain.structure.StructurePlacementState)pass.structureState())
                .skyweave$structureDistribution();
        if (distribution != null) distribution.bind(pass);
        StructureWorldScope.run(distribution, () -> original.call(pass));
    }

    @WrapMethod(method = "tryGenerateValidatedStructure")
    private boolean selectedDestination(StructurePlacementPassContext pass, StructureSet.StructureSelectionEntry entry,
            Operation<Boolean> original) {
        var distribution = ((design.inherent.skyweavecompat.terrain.structure.StructurePlacementState)pass.structureState())
                .skyweave$structureDistribution();
        var id = entry.structure().unwrapKey().map(key -> key.location()).orElse(null);
        if (distribution != null && distribution.manages(entry) && id != null
                && !distribution.permitsVariant(id, pass.chunkPos().x, pass.chunkPos().z)) return false;
        return StructurePlacementScope.run(pass, entry, () -> original.call(pass, entry));
    }
}
