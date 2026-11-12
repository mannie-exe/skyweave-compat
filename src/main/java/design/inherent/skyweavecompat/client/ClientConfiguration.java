package design.inherent.skyweavecompat.client;

import design.inherent.skyweavecompat.SkyweaveCompat;
import design.inherent.skyweavecompat.Feature;
import design.inherent.skyweavecompat.OptionalIntegrations;
import design.inherent.skyweavecompat.questlog.client.JournalPreferences;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = SkyweaveCompat.ID, dist = Dist.CLIENT)
public final class ClientConfiguration {
    public ClientConfiguration(ModContainer container, IEventBus modBus) {
        if (OptionalIntegrations.enabled(Feature.WAYFINDER)) {
            WayfinderClient.register(modBus);
            container.registerConfig(ModConfig.Type.CLIENT,WayfinderPreferences.SPEC,
                    design.inherent.skyweavecompat.configuration.ConfigLocations.registeredName(WayfinderPreferences.FILE));
        }
        if (OptionalIntegrations.enabled(Feature.CLIMATE)) ClimateClient.register();
        modBus.addListener(ClientResourceRevision::register);
        if (OptionalIntegrations.enabled(Feature.ISLAND_CLOUDS)) modBus.addListener(IslandSky::register);
        if (OptionalIntegrations.enabled(Feature.CONSTRUCT_OCCLUSION))
            modBus.addListener((net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) ->
                    event.enqueueWork(ConstructOcclusion::register));
        var glassFeatures = PlainGlassModels.FEATURES.stream().filter(OptionalIntegrations::enabled)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (!glassFeatures.isEmpty())
            modBus.addListener(net.neoforged.bus.api.EventPriority.LOWEST,
                    (net.neoforged.neoforge.client.event.ModelEvent.ModifyBakingResult event) ->
                            PlainGlassModels.wrap(event, id -> PlainGlassModels.target(id, glassFeatures)));
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationOverview::new);
        if (OptionalIntegrations.enabled(Feature.REDSTONE_RENDERING))
            modBus.addListener((net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) -> event.enqueueWork(() ->
                    net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(
                            net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.parse("create_redstone_additions:inverter")),
                            net.minecraft.client.renderer.RenderType.cutout())));
        if (OptionalIntegrations.enabled(Feature.LINEAR_PONDER))
            design.inherent.skyweavecompat.linear.client.LinearPonder.register();
        if (OptionalIntegrations.enabled(Feature.QUESTLOG_INTERFACE)
                || OptionalIntegrations.enabled(Feature.QUESTLOG_NOTIFICATIONS)) {
            container.registerConfig(ModConfig.Type.CLIENT, JournalPreferences.SPEC,
                    design.inherent.skyweavecompat.configuration.ConfigLocations.registeredName(JournalPreferences.FILE));
        }
    }
}
