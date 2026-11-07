package design.inherent.skyweavecompat.terrain;

import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

/** Loaded only when the pinned Sky Archipelago preset adaptation is active. */
public final class TerrainIntegration {
    private TerrainIntegration() {}

    public static void register() {
        var directory = FMLPaths.CONFIGDIR.get();
        GenerationConfiguration.load(directory);
        InteriorConfiguration.load(directory);
        RiverConfiguration.load(directory);
        OceanConfiguration.load(directory);
        if (design.inherent.skyweavecompat.OptionalIntegrations.enabled(design.inherent.skyweavecompat.Feature.STRUCTURE_DISTRIBUTION)) {
            design.inherent.skyweavecompat.terrain.structure.StructureConfiguration.load(directory);
            design.inherent.skyweavecompat.terrain.structure.StructureLevelIntegration.register();
        }
        design.inherent.skyweavecompat.terrain.city.CityConfiguration.load(directory);
        NeoForge.EVENT_BUS.addListener((ServerAboutToStartEvent event) -> GenerationGuard.verify(event.getServer()));
    }
}
