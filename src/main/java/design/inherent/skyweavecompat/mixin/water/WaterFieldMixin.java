package design.inherent.skyweavecompat.mixin.water;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import design.inherent.skyweavecompat.water.IslandWaterColumns;
import design.inherent.skyweavecompat.water.WaterField;
import net.minecraft.world.level.levelgen.RandomState;
import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;
import org.sathrek.sky_archipelago.worldgen.generator.field.internal.ChunkTerrainSnapshotResolver;
import org.sathrek.sky_archipelago.worldgen.generator.field.internal.IslandCandidateIndex;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = IslandField.class, remap = false)
public abstract class WaterFieldMixin implements WaterField {
    @Unique private volatile IslandWaterColumns skyweave$water;
    @Shadow @Final private org.sathrek.sky_archipelago.worldgen.generator.field.internal.IslandColumnResolver columnResolver;

    @Override public synchronized void skyweave$water(SkyIslandChunkGenerator generator, RandomState random) {
        if (skyweave$water == null) {
            skyweave$water = new IslandWaterColumns(generator, random);
            ((design.inherent.skyweavecompat.terrain.InteriorResolver)(Object)columnResolver)
                    .skyweave$waterProtection(skyweave$water::protectedFloor);
        }
    }

    @WrapOperation(method = "createChunkColumnResolver", at = @At(value = "NEW",
            target = "org/sathrek/sky_archipelago/worldgen/generator/field/internal/ChunkTerrainSnapshotResolver"))
    private ChunkTerrainSnapshotResolver waterColumns(int x, int z, int size, IslandCandidateIndex index,
            ChunkTerrainSnapshotResolver.ExactColumnResolver raw, ChunkTerrainSnapshotResolver.ExactColumnResolver cached,
            ChunkTerrainSnapshotResolver.HydrologyApplicator hydrology, Operation<ChunkTerrainSnapshotResolver> original) {
        var water = skyweave$water;
        return water == null ? original.call(x, z, size, index, raw, cached, hydrology)
                : original.call(x, z, size, index, raw,
                        (ChunkTerrainSnapshotResolver.ExactColumnResolver)(px, pz, candidates) -> water.apply(px, pz, cached.resolve(px, pz, candidates)),
                        (ChunkTerrainSnapshotResolver.HydrologyApplicator)(px, pz, column) -> water.apply(px, pz, hydrology.apply(px, pz, column)));
    }
}
