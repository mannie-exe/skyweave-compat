package design.inherent.skyweavecompat.terrain;

import org.sathrek.sky_archipelago.config.SkyIslandSettings;

public interface GeologicalField {
    /** Pre-Y placement origin only: one cluster frame preserves the native attempt budget.
     * Physical point consumers use IslandContext instead. Null in void/unadapted fields. */
    GeologicalFrame skyweave$originFrameAt(int x, int z, SkyIslandSettings settings);
}
