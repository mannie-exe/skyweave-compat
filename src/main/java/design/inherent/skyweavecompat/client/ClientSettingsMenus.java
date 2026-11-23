package design.inherent.skyweavecompat.client;

import design.inherent.reform.definition.DocumentId;
import design.inherent.reform.definition.FieldPath;
import design.inherent.reform.minecraft.client.ClientChoiceScreen;
import design.inherent.reform.minecraft.client.ClientEditingWorkflow;
import design.inherent.reform.minecraft.client.ClientForm;
import design.inherent.reform.minecraft.client.ClientFormOptions;
import design.inherent.reform.minecraft.client.ClientKeyBindings;
import design.inherent.reform.minecraft.client.ClientWorkflowLayout;
import design.inherent.reform.minecraft.client.IntegratedSettings;
import design.inherent.reform.owner.ApplicationEffects;
import design.inherent.skyweavecompat.Feature;
import design.inherent.skyweavecompat.FeatureSettings;
import design.inherent.skyweavecompat.OptionalIntegrations;
import design.inherent.skyweavecompat.SkyweaveCompat;
import design.inherent.skyweavecompat.configuration.NativeSettingsPolicy;
import design.inherent.skyweavecompat.configuration.PolicyDefinitions;
import design.inherent.skyweavecompat.configuration.SettingsCatalog;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/** The native entry chooses a real context; re:form owns each complete editing workflow. */
final class ClientSettingsMenus {
    private final TerrainProfileForms terrain;
    private final StartupFeatureForm startup;
    private final NativePreferences nativePreferences;
    private final JsonPreferences json;
    private final ClientKeyBindings keys;
    private SettingsFiles files;
    private ShaderOperation shaders;

    ClientSettingsMenus(IEventBus bus, ModContainer container, TerrainProfileForms terrain,
            StartupFeatureForm startup, NativePreferences nativePreferences) {
        this.terrain = terrain;
        this.startup = startup;
        this.nativePreferences = nativePreferences;
        json = new JsonPreferences(bus, container);
        keys = ClientKeyBindings.clientKeys(design.inherent.reform.neoforge.client.NeoForgeClientRegistration.of(bus, container), id("controls"), () ->
                Arrays.stream(Minecraft.getInstance().options.keyMappings)
                        .filter(key -> key.getName().startsWith("key.skyweave_compat.")).toList());
        if (OptionalIntegrations.enabled(Feature.SHADER_PATCHES))
            bus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(() -> shaders = new ShaderOperation()));
    }

    Screen screen(Screen parent) {
        var client = Minecraft.getInstance();
        return new ClientChoiceScreen(parent, Component.translatable("skyweave_compat.configuration.title"),
                text("context_choice_help"), List.of(
                        new ClientChoiceScreen.Choice("local_files", text("local_files"), text("page_scope"),
                                Optional::empty, () -> client.setScreen(local(client.screen))),
                        new ClientChoiceScreen.Choice("running_server", text("running_server"), text("server_policy_scope"),
                                this::serverBlocked, () -> client.setScreen(server(client.screen)))),
                Component.translatable("gui.done"));
    }

    private Optional<Component> serverBlocked() {
        var client = Minecraft.getInstance();
        if (client.getSingleplayerServer() == null || client.player == null)
            return Optional.of(text("server_requires_world"));
        if (!client.player.hasPermissions(2)) return Optional.of(text("server_requires_operator"));
        return Optional.empty();
    }

    private Screen local(Screen parent) {
        if (files == null) files = new SettingsFiles();
        var related = new LinkedHashMap<String, ClientForm>();
        for (var entry : SettingsCatalog.FILES) {
            if (entry.filename().equals(FeatureSettings.FILE)) continue;
            ClientForm form;
            if (TerrainProfileForms.owns(entry.filename()) && terrain != null)
                form = terrain.forms().stream().filter(value -> value.route().equals(DocumentId.parse(entry.id()))).findFirst().orElseThrow();
            else if (entry.filename().endsWith(".toml")) form = nativePreferences.form(entry);
            else form = json.form(entry);
            related.put(entry.filename(), form);
        }
        var forms = new LinkedHashMap<String, ClientForm>();
        forms.put(FeatureSettings.FILE, startup.form(related));
        forms.putAll(related);
        var groups = catalog(forms);
        groups.add(new ClientWorkflowLayout.Group(List.of(
                category("catalog/files", "status_files", "files_status_help")), List.of(), files.actions()));
        var controlForm = keys.form(text("controls"));
        forms.put("controls", controlForm);
        groups.add(new ClientWorkflowLayout.Group(List.of(
                category("catalog/interface", "category.interface", "page_scope"),
                category("catalog/interface/skyweave_compat", "mod.skyweave_compat", "page_scope"),
                category("catalog/interface/skyweave_compat/controls", "controls", "controls_help")),
                List.of(placement(id("controls/keys"), controlForm, controlForm.title(), Component.empty(), Optional.empty(), Set.of())
                        .inSection(new ClientWorkflowLayout.Section(id("section/controls"), text("controls"), Component.empty())
                                .withEyebrow(text("mod.skyweave_compat")))), List.of()));
        var reviews = terrain == null ? List.<ClientWorkflowLayout.ReviewScope>of() : List.of(terrain.review());
        return ClientEditingWorkflow.open(parent, text("local_files"), List.copyOf(forms.values()),
                new ClientWorkflowLayout(groups, reviews)).screen();
    }

    private ArrayList<ClientWorkflowLayout.Group> catalog(Map<String, ClientForm> forms) {
        var groups = new ArrayList<ClientWorkflowLayout.Group>();
        for (var page : SettingsCatalog.pages()) for (var section : page.sections()) {
            var placements = new ArrayList<ClientWorkflowLayout.Placement>();
            var actions = new ArrayList<ClientWorkflowLayout.Action<?>>();
            String prefix = "catalog/" + page.id();
            for (var item : section.items()) {
                if (item instanceof SettingsCatalog.Fix fix) {
                    var entry = SettingsCatalog.entry(fix.feature());
                    placements.add(placement(id("features/" + fix.feature().key), forms.get(FeatureSettings.FILE),
                            Component.translatable(entry.labelKey()), Component.empty(),
                            Optional.of(FieldPath.ROOT.field(fix.feature().key)), Set.of(ApplicationEffects.Timing.GAME_RESTART)));
                } else if (item instanceof SettingsCatalog.Preference preference) {
                    String key = switch (preference.key()) {
                        case "images" -> "itemImages";
                        case "symbols" -> "conditionSymbols";
                        case "notifications" -> "notifications";
                        default -> throw new IllegalArgumentException("Unknown journal preference");
                    };
                    placements.add(placement(id("journal/" + preference.key()), forms.get("skyweave_compat-journal-client.toml"),
                            Component.translatable("skyweave_compat.journal." + preference.key()),
                            Component.translatable("skyweave_compat.journal." + preference.key() + "_description"),
                            Optional.of(FieldPath.ROOT.field(key)), Set.of(ApplicationEffects.Timing.OWNER_MANAGED)));
                } else if (item instanceof SettingsCatalog.File file) {
                    var entry = file.entry();
                    var form = forms.get(entry.filename());
                    if (entry.help().equals("rendering_client") && !nativePreferences.fields(entry).isEmpty()) {
                        nativePreferences.fields(entry).stream().filter(field -> entry.presents(page, field.key())).forEach(field ->
                                placements.add(placement(id("files/" + page.id() + "/" + entry.help() + "/" + field.key().toLowerCase(Locale.ROOT)), form,
                                        field.spec().getTranslationKey() == null ? Component.literal(field.key())
                                                : Component.translatable(field.spec().getTranslationKey()),
                                        Component.empty(),
                                        Optional.of(NativeSettingsPolicy.path(field)), Set.of(NativeSettingsPolicy.timing(entry.timing(field.key()))))));
                    } else {
                        var documentFrame = new ClientWorkflowLayout.Section(id(section.id() + "/" + entry.help()),
                                Component.translatable(entry.labelKey()), Component.empty()).withEyebrow(
                                Component.translatable("skyweave_compat.settings.document_context",
                                        text("mod." + page.label()), text(section.kind().label)));
                        placements.add(placement(id("files/" + page.id() + "/" + entry.help()), form,
                                Component.translatable(entry.labelKey()), SettingsPresentation.documentHelp(entry),
                                Optional.empty(), nativePreferences.fields(entry).isEmpty()
                                        ? Set.of(NativeSettingsPolicy.timing(entry.timing()))
                                        : nativePreferences.fields(entry).stream().map(field -> NativeSettingsPolicy.timing(field.timing(entry.filename())))
                                                .collect(java.util.stream.Collectors.toSet())).inSection(documentFrame));
                    }
                } else if (item instanceof SettingsCatalog.ShaderWorkspace && shaders != null) {
                    actions.add(shaders.action());
                    actions.add(shaders.statusAction());
                }
            }
            var frame = new ClientWorkflowLayout.Section(id(section.id()), text(section.kind().label),
                    section.kind() == SettingsCatalog.SectionKind.FIXES ? text("after_save.game_restart") : Component.empty())
                    .withEyebrow(text("mod." + page.label()));
            if (!placements.isEmpty() || !actions.isEmpty()) groups.add(new ClientWorkflowLayout.Group(List.of(
                    category("catalog/" + page.category().name().toLowerCase(Locale.ROOT),
                            "category." + page.category().name().toLowerCase(Locale.ROOT), "page_scope"),
                    category(prefix, "mod." + page.label(), "page_scope"),
                    category(prefix + "/" + section.kind().name().toLowerCase(Locale.ROOT), section.kind().label, "page_scope")),
                    placements.stream().map(placement -> placement.section().isPresent() ? placement : placement.inSection(frame)).toList(), actions));
        }
        return groups;
    }

    private Screen server(Screen parent) {
        var forms = List.of(
                policy("skyweave_compat-wayfinder.json", PolicyDefinitions.WAYFINDER),
                policy("skyweave_compat-search.json", PolicyDefinitions.SEARCH));
        var groups = new ArrayList<ClientWorkflowLayout.Group>();
        for (var form : forms) {
            var entry = SettingsCatalog.document(form.route().toString());
            var page = SettingsCatalog.page(entry.group());
            groups.add(new ClientWorkflowLayout.Group(List.of(
                    category("server/" + page.id(), "mod." + page.label(), "server_policy_scope")),
                    List.of(placement(id("server/" + entry.help()), form, form.title(), text("server_file_policy"),
                            Optional.empty(), Set.of(ApplicationEffects.Timing.OWNER_PUBLICATION))
                            .inSection(new ClientWorkflowLayout.Section(id("section/server/" + entry.help()),
                                    form.title(), text("server_policy_scope"))
                                    .withEyebrow(Component.translatable("skyweave_compat.settings.document_context",
                                            text("mod." + page.label()), text("server_policy"))))), List.of()));
        }
        return ClientEditingWorkflow.open(parent, text("running_server"), forms, new ClientWorkflowLayout(groups, List.of())).screen();
    }

    private static <T> ClientForm policy(String filename, design.inherent.reform.definition.DocumentDefinition<T> definition) {
        var entry = SettingsCatalog.file(filename);
        return IntegratedSettings.form(Component.translatable(entry.labelKey()), DocumentId.parse(entry.id()), 2, definition,
                new ClientFormOptions<>(SettingsPresentation.policy(design.inherent.skyweavecompat.configuration.JsonSettings.find(filename)), Map.of()));
    }

    private static ClientWorkflowLayout.Placement placement(DocumentId id, ClientForm form, Component title, Component help,
            Optional<FieldPath> field, Set<ApplicationEffects.Timing> timings) {
        return new ClientWorkflowLayout.Placement(id, form.route(), title, field, help, timings);
    }

    private static ClientWorkflowLayout.Category category(String id, String label, String help) {
        return new ClientWorkflowLayout.Category(id(id), text(label), text(help));
    }
    private static DocumentId id(String path) {
        try { return new DocumentId(SkyweaveCompat.ID, path); }
        catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("Invalid settings navigation identity: " + path, invalid); }
    }
    private static Component text(String key) { return Component.translatable("skyweave_compat.settings." + key); }

}
