package design.inherent.skyweavecompat.client;

import design.inherent.reform.definition.DocumentId;
import design.inherent.reform.definition.FieldPath;
import design.inherent.reform.neoforge.client.ClientEditingWorkflow;
import design.inherent.reform.neoforge.client.ClientFileSettings;
import design.inherent.reform.neoforge.client.ClientWorkflowLayout;
import design.inherent.reform.presentation.FormCatalog;
import design.inherent.reform.presentation.FormPresentation;
import design.inherent.skyweavecompat.Feature;
import design.inherent.skyweavecompat.FeatureSettings;
import design.inherent.skyweavecompat.OptionalIntegrations;
import design.inherent.skyweavecompat.SkyweaveCompat;
import design.inherent.skyweavecompat.configuration.ReformDocuments;
import design.inherent.skyweavecompat.configuration.SettingsCatalog;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
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

    void open(Screen parent, Feature field) {
        var title = Component.translatable(SettingsCatalog.file(FeatureSettings.FILE).labelKey());
        var sections = Arrays.stream(Feature.values()).map(feature -> {
            var entry = SettingsCatalog.entry(feature);
            return new FormCatalog.Section(FieldPath.ROOT.field(feature.key), entry.labelKey(),
                    Component.translatableWithFallback(entry.labelKey(), feature.key).getString(),
                    Component.translatableWithFallback(entry.summaryKey(), "").getString());
        }).toList();
        var form = binding.form(title, FormPresentation.EMPTY.withCatalog(new FormCatalog(sections)));
        var groups = Arrays.stream(SettingsCatalog.Category.values()).map(category -> new ClientWorkflowLayout.Group(
                Component.translatable("skyweave_compat.settings.category." + category.name().toLowerCase(Locale.ROOT)),
                SettingsCatalog.pages(category).stream().flatMap(page -> page.features().stream()).map(feature ->
                        new ClientWorkflowLayout.Placement(placement(feature), form.route(),
                                Component.translatable(SettingsCatalog.entry(feature).labelKey()),
                                Optional.of(FieldPath.ROOT.field(feature.key)))).toList(), List.of())).toList();
        var workflow = ClientEditingWorkflow.open(parent, title, List.of(form), new ClientWorkflowLayout(groups, List.of()));
        Minecraft.getInstance().setScreen(workflow.screen());
        if (field != null) workflow.showPlacement(placement(field));
    }

    private static DocumentId placement(Feature feature) { return new DocumentId(SkyweaveCompat.ID, "features/" + feature.key); }
}
