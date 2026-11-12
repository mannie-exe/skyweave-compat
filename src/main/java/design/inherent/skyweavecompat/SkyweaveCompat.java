package design.inherent.skyweavecompat;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import design.inherent.skyweavecompat.placement.DeliveryChunks;
import net.neoforged.bus.api.IEventBus;

@Mod(SkyweaveCompat.ID)
public final class SkyweaveCompat {
    public static final String ID = "skyweave_compat";

    public SkyweaveCompat(ModContainer container, IEventBus modBus) {
        var selection = OptionalIntegrations.selection();
        if (selection.enabled(Feature.WAYFINDER)) design.inherent.skyweavecompat.wayfinder.Wayfinder.register(modBus);
        com.mojang.logging.LogUtils.getLogger().info("Skyweave: Compatibility Patches active adaptations: {}", selection.active());
        com.mojang.logging.LogUtils.getLogger().debug("Skyweave: Compatibility Patches inactive adaptations: {}", selection.inactive());
        if (selection.enabled(Feature.REDSTONE_SCRAPING))
            design.inherent.skyweavecompat.redstone.RedstoneScraping.register();
        if (selection.enabled(Feature.FTB_ITEM_FILTERS))
            modBus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) ->
                    event.enqueueWork(design.inherent.skyweavecompat.quests.QuestItemFilters::register));
        if (selection.enabled(Feature.SABLE_GRAVES))
            design.inherent.skyweavecompat.recovery.ShipGraves.register();
        if (OptionalIntegrations.skyVillages()) {
            container.registerConfig(ModConfig.Type.COMMON, NeighborConfig.SPEC,
                    design.inherent.skyweavecompat.configuration.ConfigLocations.registeredName("skyweave_compat-common.toml"));
            NeoForge.EVENT_BUS.addListener((ServerAboutToStartEvent event) -> NeighborConfig.beginServer());
        }
        if (selection.enabled(Feature.TERRAIN)) {
            if (selection.enabled(Feature.GROUND_SUPPORT)) {
                design.inherent.skyweavecompat.terrain.structure.NaturalStructureInputs.register(modBus);
                design.inherent.skyweavecompat.terrain.structure.GroundSupportProofs.register(modBus);
            }
            design.inherent.skyweavecompat.terrain.TerrainIntegration.register();
            if (selection.enabled(Feature.CLIMATE)) design.inherent.skyweavecompat.climate.ClimateRuntime.register(modBus);
        } else {
            // This checks Compat's own save marker without loading or modifying any optional mod.
            NeoForge.EVENT_BUS.addListener((ServerAboutToStartEvent event) -> {
                var root = event.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
                if (java.nio.file.Files.exists(root.resolve(design.inherent.skyweavecompat.terrain.GenerationIdentity.FILE)))
                    throw new IllegalStateException("This save requires Skyweave: Compatibility Patches terrain.preset and Sky Archipelago 1.4. "
                            + "Restore its recorded configuration; automatic world conversion is unsupported.");
            });
        }
        if (selection.enabled(Feature.TERRAIN) || selection.enabled(Feature.LINEAR_BOOTSTRAP))
            modBus.addListener(IntegrationPacks::register);
        if (net.neoforged.fml.loading.FMLEnvironment.dist.isDedicatedServer() && OptionalIntegrations.blueMap()) {
            design.inherent.skyweavecompat.bluemap.BlueMapIntegration.register();
        }
        if (selection.enabled(Feature.DELIVERY_CHUNKS))
            NeoForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> DeliveryChunks.stop(event.getServer()));
    }
}
