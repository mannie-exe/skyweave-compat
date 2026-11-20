package design.inherent.skyweavecompat.client;

import design.inherent.reform.definition.FieldPath;
import design.inherent.reform.neoforge.client.ClientFileSettings;
import design.inherent.reform.neoforge.client.ClientForm;
import design.inherent.reform.owner.ApplicationEffects;
import design.inherent.reform.owner.FileOwner;
import design.inherent.skyweavecompat.configuration.JsonSettings;
import design.inherent.skyweavecompat.configuration.NativeSettingsPolicy;
import design.inherent.skyweavecompat.configuration.ReformJsonStore;
import design.inherent.skyweavecompat.configuration.SettingsCatalog;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLPaths;

/** Local files retain their real format and stage values for their existing runtime owners. */
final class JsonPreferences {
    private record Binding(ClientFileSettings<Map<String, Object>> owner, JsonSettings.Definition source) {}
    private final Map<String, Binding> bindings = new LinkedHashMap<>();
    private final Map<String, String> refused = new LinkedHashMap<>();

    JsonPreferences(IEventBus bus, ModContainer container) {
        bus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(() -> {
            for (var entry : SettingsCatalog.FILES) {
                if (!entry.filename().endsWith(".json") || TerrainProfileForms.owns(entry.filename())
                        || entry.help().equals("hoofprint_zoom") || !NativePreferences.admitted(entry)) continue;
                var source = JsonSettings.find(entry.filename());
                if (source == null) {
                    refused.put(entry.filename(), "The optional native codec is unavailable");
                    continue;
                }
                try {
                    var store = ReformJsonStore.map(FMLPaths.CONFIGDIR.get(), source);
                    var effects = new ApplicationEffects(java.util.List.of(new ApplicationEffects.Rule(FieldPath.ROOT,
                            Set.of(NativeSettingsPolicy.timing(entry.timing())),
                            Component.translatable("skyweave_compat.settings.after_save."
                                    + entry.timing().name().toLowerCase(java.util.Locale.ROOT)).getString())));
                    var owner = ClientFileSettings.stagedFile(bus, container, store,
                            FileOwner.Initialization.DEFER_MISSING, effects);
                    bindings.put(entry.filename(), new Binding(owner, source));
                } catch (RuntimeException failure) { refused.put(entry.filename(), String.valueOf(failure.getMessage())); }
            }
        }));
    }

    ClientForm form(SettingsCatalog.FileEntry entry) {
        if (entry.help().equals("hoofprint_zoom") && NativePreferences.admitted(entry)) return HoofprintPreferences.form();
        var binding = bindings.get(entry.filename());
        return binding == null ? NativePreferences.unavailable(entry,
                refused.getOrDefault(entry.filename(), "The adaptation or its settings owner is unavailable"))
                : binding.owner().form(Component.translatable(entry.labelKey()), SettingsPresentation.json(binding.source()));
    }
}
