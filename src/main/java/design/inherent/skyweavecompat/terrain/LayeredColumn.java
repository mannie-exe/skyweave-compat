package design.inherent.skyweavecompat.terrain;

import org.sathrek.sky_archipelago.worldgen.generator.field.TerrainColumn;
import java.util.List;

/** Exterior intervals survive internal carving; the native segments contain actual baseline solid. */
public interface LayeredColumn {
    boolean skyweave$hasExterior();
    boolean skyweave$requiresExactInterior();
    void skyweave$requireExactSampling();
    List<TerrainColumn> skyweave$exterior();
    void skyweave$exterior(List<TerrainColumn> exterior, boolean exactInterior);
}
