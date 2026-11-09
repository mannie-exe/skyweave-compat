package design.inherent.skyweavecompat.terrain;

import org.sathrek.sky_archipelago.worldgen.generator.field.model.ColumnData;
import net.minecraft.world.level.levelgen.RandomState;
import org.sathrek.sky_archipelago.config.SkyIslandSettings;
import org.sathrek.sky_archipelago.worldgen.generator.field.TerrainColumn;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;
import java.util.List;

public interface InteriorField {
    void skyweave$configureInteriors(RandomState random, InteriorProfile profile);
    List<TerrainColumn> skyweave$exteriorAt(int x, int z, SkyIslandSettings settings);
    /** Native exterior of one host, excluding cavities and unrelated overlapping islands. */
    TerrainColumn skyweave$exteriorOf(org.sathrek.sky_archipelago.worldgen.generator.field.model.IslandDescriptor host,
            int x, int z, SkyIslandSettings settings);
    /** Detached additive edge lobes are not a continuation of the host's drainage surface. */
    boolean skyweave$isFringe(org.sathrek.sky_archipelago.worldgen.generator.field.model.IslandDescriptor host,
            int x, int z, SkyIslandSettings settings);
    ColumnData skyweave$planningColumn(int x, int z, SkyIslandSettings settings, TerrainStage stage);
}
