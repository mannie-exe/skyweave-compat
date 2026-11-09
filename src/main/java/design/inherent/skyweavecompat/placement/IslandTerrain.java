package design.inherent.skyweavecompat.placement;

import design.inherent.skyweavecompat.terrain.*;
import org.sathrek.sky_archipelago.config.SkyIslandSettings;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;
import org.sathrek.sky_archipelago.worldgen.generator.field.model.*;
import org.sathrek.sky_archipelago.worldgen.structure.StructureSupportContext;

/** Exterior clearance does not trigger completed river planning. All terrain blocks placement;
 * only a substantial non-fringe body in the intended cluster proves a nearby host. */
public record IslandTerrain(IslandField field, SkyIslandSettings settings, VillagePlan.Anchor anchor) implements NeighborRule.Terrain {
    public IslandTerrain(StructureSupportContext context, VillagePlan.Anchor anchor) {
        this(context.islandField(), context.settings(), anchor);
    }
    public IslandTerrain(StructureSupportContext context) { this(context, null); }
    private ColumnData column(int x, int z) {
        return (Object)field instanceof InteriorField planning
                ? planning.skyweave$planningColumn(x, z, settings, TerrainStage.EXTERIOR)
                : field.sampleChunkTerrainSnapshot(new net.minecraft.world.level.ChunkPos(Math.floorDiv(x, 16), Math.floorDiv(z, 16)), settings).columnDataAt(x, z);
    }
    @Override public boolean exists(int x, int z) { return column(x, z).profile().exists(); }
    @Override public int islandRadius(int x, int z) {
        var column = column(x, z);
        var descriptor = column.dominantDescriptor();
        if (descriptor == null || !column.profile().exists() || column.profile().topY() - column.profile().bottomY() + 1 < 16
                || (Object)field instanceof InteriorField planning && planning.skyweave$isFringe(descriptor, x, z, settings)) return -1;
        if (anchor != null) {
            var clusters = (ClusterField)(Object)field;
            var cell = clusters.skyweave$owner(descriptor, settings);
            var members = clusters.skyweave$members(cell.x(), cell.z(), settings);
            if (members.isEmpty() || members.getFirst().seed() != anchor.seed()) return -1;
        }
        return descriptor.maxRadius();
    }
}
