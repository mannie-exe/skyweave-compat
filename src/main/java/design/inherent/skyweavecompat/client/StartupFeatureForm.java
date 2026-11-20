package design.inherent.skyweavecompat.client;

import design.inherent.reform.definition.FieldPath;
import design.inherent.reform.neoforge.client.ClientFileSettings;
import design.inherent.reform.neoforge.client.ClientForm;
import design.inherent.reform.presentation.FormCatalog;
import design.inherent.reform.presentation.FormPresentation;
import design.inherent.skyweavecompat.Feature;
import design.inherent.skyweavecompat.FeatureSettings;
import design.inherent.skyweavecompat.OptionalIntegrations;
import design.inherent.skyweavecompat.configuration.ReformDocuments;
import design.inherent.skyweavecompat.configuration.SettingsCatalog;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.loading.LoadingModList;

/** All feature placements share the actual next-process document and its draft. */
final class StartupFeatureForm {
    private final ClientFileSettings<FeatureSettings> binding;

    StartupFeatureForm(IEventBus bus, ModContainer container) {
        var active = OptionalIntegrations.startupSettings();
        var versions = new LinkedHashMap<String, String>();
        LoadingModList.get().getMods().forEach(mod -> versions.put(mod.getModId(), mod.getVersion().toString()));
        var store = ReformDocuments.startup(FMLPaths.CONFIGDIR.get(), active, versions, true);
        binding = ClientFileSettings.startupFile(bus, container, store,
                Optional.of(ReformDocuments.selectedStartup(store.definition(), active)));
    }

    ClientForm form() {
        var title = Component.translatable(SettingsCatalog.file(FeatureSettings.FILE).labelKey());
        var sections = Arrays.stream(Feature.values()).map(feature -> {
            var entry = SettingsCatalog.entry(feature);
            return new FormCatalog.Section(FieldPath.ROOT.field(feature.key), entry.labelKey(),
                    Component.translatableWithFallback(entry.labelKey(), feature.key).getString(),
                    Component.translatableWithFallback(entry.summaryKey(), "").getString());
        }).toList();
        return binding.form(title, FormPresentation.EMPTY.withCatalog(new FormCatalog(sections)));
    }

}
