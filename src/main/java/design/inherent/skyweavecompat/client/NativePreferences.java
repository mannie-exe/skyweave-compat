package design.inherent.skyweavecompat.client;

import design.inherent.reform.definition.DocumentId;
import design.inherent.reform.neoforge.client.ClientForm;
import design.inherent.reform.neoforge.client.ClientRegisteredSettings;
import design.inherent.reform.neoforge.configuration.RegisteredConfig;
import design.inherent.skyweavecompat.OptionalIntegrations;
import design.inherent.skyweavecompat.SkyweaveCompat;
import design.inherent.skyweavecompat.configuration.ConfigLocations;
import design.inherent.skyweavecompat.configuration.NativeFields;
import design.inherent.skyweavecompat.configuration.NativeSettingsPolicy;
import design.inherent.skyweavecompat.configuration.SettingsCatalog;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.config.ModConfigs;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/** One admitted shared binding per actual native CLIENT or local COMMON registration. */
final class NativePreferences {
    private record Binding(ClientRegisteredSettings owner, List<NativeFields.Field> fields) {}
    private final Map<String, Binding> bindings = new LinkedHashMap<>();
    private final Map<String, String> refused = new LinkedHashMap<>();

    NativePreferences(IEventBus bus, ModContainer container) {
        bus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(() -> {
            for (var entry : SettingsCatalog.FILES) {
                if (!entry.filename().endsWith(".toml") || !admitted(entry)) continue;
                try { bind(bus, container, entry); }
                catch (RuntimeException failure) { refused.put(entry.filename(),
                        failure.getMessage() == null || failure.getMessage().isBlank()
                                ? failure.getClass().getSimpleName() : failure.getMessage()); }
            }
        }));
    }

    private void bind(IEventBus bus, ModContainer container, SettingsCatalog.FileEntry entry) {
        var config = java.util.stream.Stream.of(ModConfig.Type.CLIENT, ModConfig.Type.COMMON)
                .flatMap(type -> ModConfigs.getConfigSet(type).stream())
                .filter(value -> value.getFileName().equals(ConfigLocations.registeredName(entry.filename())))
                .findFirst().orElseThrow(() -> new IllegalStateException("The native configuration is not registered"));
        if (!(config.getSpec() instanceof ModConfigSpec spec)) throw new IllegalStateException("Unsupported native configuration owner");
        var fields = NativeFields.collect(spec);
        var id = NativeSettingsPolicy.nativeId(entry, config);
        var route = DocumentId.parse(entry.id());
        final ClientRegisteredSettings binding;
        if (config.getModId().equals(SkyweaveCompat.ID)) binding = ClientRegisteredSettings.registeredNeoForge(
                bus, container, id, config, NativeSettingsPolicy.explicitTypes(fields), NativeSettingsPolicy.LIMITS, policy(entry, fields));
        else {
            var gate = NativeSettingsPolicy.delegation(entry, config, fields);
            fields = fields.stream().filter(field -> gate.fields().contains(NativeSettingsPolicy.path(field))).toList();
            binding = ClientRegisteredSettings.delegatedNeoForge(bus, container, route, config, id, gate,
                    NativeSettingsPolicy.explicitTypes(fields), NativeSettingsPolicy.LIMITS, policy(entry, fields));
        }
        bindings.put(entry.filename(), new Binding(binding, fields));
        if (entry.help().equals("journal"))
            design.inherent.skyweavecompat.questlog.client.JournalPreferences.bind(binding);
    }

    private static RegisteredConfig.Policy policy(SettingsCatalog.FileEntry entry, List<NativeFields.Field> fields) {
        return new RegisteredConfig.Policy(candidate -> NativeSettingsPolicy.validate(entry, candidate),
                NativeSettingsPolicy.effects(entry, fields));
    }

    ClientForm form(SettingsCatalog.FileEntry entry) {
        var binding = bindings.get(entry.filename());
        if (binding != null) return binding.owner().form(Component.translatable(entry.labelKey()),
                SettingsPresentation.nativeFields(entry, binding.fields()));
        String reason = refused.getOrDefault(entry.filename(), "The adaptation or its native owner is unavailable");
        return unavailable(entry, reason);
    }

    Screen screen(Screen parent, SettingsCatalog.FileEntry entry) {
        var binding = bindings.get(entry.filename());
        if (binding == null) throw new IllegalStateException("The native preference binding is unavailable");
        return binding.owner().screen(parent, Component.translatable(entry.labelKey()),
                SettingsPresentation.nativeFields(entry, binding.fields()));
    }

    boolean available(SettingsCatalog.FileEntry entry) {
        var binding = bindings.get(entry.filename());
        return binding != null && binding.owner().status().lifecycle() == ClientRegisteredSettings.Lifecycle.READY;
    }

    List<NativeFields.Field> fields(SettingsCatalog.FileEntry entry) {
        var binding = bindings.get(entry.filename());
        return binding == null ? List.of() : binding.fields();
    }

    static boolean admitted(SettingsCatalog.FileEntry entry) {
        return entry.owners().stream().anyMatch(OptionalIntegrations::enabled);
    }

    static ClientForm unavailable(SettingsCatalog.FileEntry entry, String reason) {
        return ClientForm.of(DocumentId.parse(entry.id()), Component.translatable(entry.labelKey()),
                () -> new ClientForm.Availability(false, false, Component.literal(reason)),
                ignored -> { throw new IllegalStateException(reason); }, Optional.empty());
    }
}
