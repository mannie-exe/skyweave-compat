package design.inherent.skyweavecompat.mixin;

import design.inherent.skyweavecompat.terrain.GeologicalGenerator;
import design.inherent.skyweavecompat.terrain.structure.*;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;
import org.spongepowered.asm.mixin.*;

/** Replace only known Overworld families in this generator's runtime view. Registered datapack sets stay intact. */
@Mixin(ChunkGeneratorStructureState.class)
public abstract class StructurePolicyStateMixin implements StructurePlacementState {
    @Shadow @Final @Mutable private List<Holder<StructureSet>> possibleStructureSets;
    @Shadow @Final private long levelSeed;
    @Shadow @Final private BiomeSource biomeSource;
    @Shadow @Final private RandomState randomState;
    @Unique private StructureDistribution skyweave$distribution;

    @Override public void skyweave$configureStructures(SkyIslandChunkGenerator generator) {
        if (skyweave$distribution != null) throw new IllegalStateException("Structure policy already initialized");
        var field = ((GeologicalGenerator)(Object)generator).skyweave$field(randomState);
        skyweave$distribution = new StructureDistribution(generator, field, generator.resolvedSkyIslandSettings(),
                biomeSource, randomState, levelSeed, possibleStructureSets, StructureConfiguration.policy());
        possibleStructureSets = possibleStructureSets.stream().map(holder -> {
            var set = holder.value();
            var family = StructureDistribution.managedFamily(set, generator.resolvedSkyIslandSettings());
            if (family == null) return holder;
            return Holder.direct(new StructureSet(set.structures(), new ArchipelagoStructurePlacement(
                    (RandomSpreadStructurePlacement)set.placement(), family, skyweave$distribution)));
        }).toList();
    }

    @Override public StructureDistribution skyweave$structureDistribution() { return skyweave$distribution; }
}
