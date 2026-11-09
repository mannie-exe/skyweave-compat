package design.inherent.skyweavecompat.placement;

import org.sathrek.sky_archipelago.config.SkyIslandSettings;
import java.util.List;

public interface VillageAnchors {
    List<VillagePlan.Anchor> skyweave$anchorsNear(int x, int z, SkyIslandSettings settings);
    VillagePlan.Anchor skyweave$anchorInCell(int cellX, int cellZ, SkyIslandSettings settings);
}
