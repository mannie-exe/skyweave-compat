package design.inherent.skyweavecompat.terrain;

import net.minecraft.world.level.LevelHeightAccessor;
import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;

/** One carving context's immutable geography binding; never changes world bounds or tunnel geometry. */
public record CarverGeology(SkyIslandChunkGenerator generator, IslandField field, ReferenceHeightContext reference) {
    public CarverGeology(SkyIslandChunkGenerator generator, IslandField field) {
        this(generator, field, new ReferenceHeightContext(generator,
                LevelHeightAccessor.create(GeologicalFrame.REFERENCE_BOTTOM, GeologicalFrame.REFERENCE_HEIGHT)));
    }

    public int originHeight(int sampled, int x, int z, int rejected) {
        var frame = ((GeologicalField)(Object)field).skyweave$originFrameAt(x, z, generator.resolvedSkyIslandSettings());
        return frame == null || !frame.containsReference(sampled) ? rejected : frame.toWorld(sampled);
    }

    public int pointHeight(int sampled, net.minecraft.core.BlockPos position, int rejected) {
        var context=((InteriorField)(Object)field).skyweave$contextAt(position.getX(),position.getZ(),generator.resolvedSkyIslandSettings());
        var owner=context.solidOwner(position.getY());
        if(owner==null)owner=context.owner(position.getY(),0);
        return owner==null || !owner.geology().containsReference(sampled) ? rejected : owner.geology().toWorld(sampled);
    }

    public int canyonHeight(int sampled, int x, int z, int rejected) {
        var column = field.sampleColumn(x, z, generator.resolvedSkyIslandSettings());
        return column.exists() ? column.topY() + sampled - GeologicalFrame.REFERENCE_SURFACE : rejected;
    }
}
