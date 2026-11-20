package design.inherent.skyweavecompat.client;

import design.inherent.reform.definition.DocumentId;
import design.inherent.reform.definition.FieldPath;
import design.inherent.reform.neoforge.client.ClientEditingWorkflow;
import design.inherent.reform.neoforge.client.ClientForm;
import design.inherent.reform.neoforge.client.ClientFormOptions;
import design.inherent.reform.neoforge.client.ClientKeyBindings;
import design.inherent.reform.neoforge.client.ClientWorkflowLayout;
import design.inherent.reform.neoforge.client.IntegratedSettings;
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
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.components.Tooltip;
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
    private ShaderOperation shaders;

    ClientSettingsMenus(IEventBus bus, ModContainer container, TerrainProfileForms terrain,
            StartupFeatureForm startup, NativePreferences nativePreferences) {
        this.terrain = terrain;
        this.startup = startup;
        this.nativePreferences = nativePreferences;
        json = new JsonPreferences(bus, container);
        keys = ClientKeyBindings.clientKeys(bus, container, id("controls"), () ->
                Arrays.stream(Minecraft.getInstance().options.keyMappings)
                        .filter(key -> key.getName().startsWith("key.skyweave_compat.")).toList());
        if (OptionalIntegrations.enabled(Feature.SHADER_PATCHES))
            bus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(() -> shaders = new ShaderOperation()));
    }

    Screen screen(Screen parent) { return new ContextScreen(parent); }

    private Screen local(Screen parent) {
        var forms = new LinkedHashMap<String, ClientForm>();
        forms.put(FeatureSettings.FILE, startup.form());
        for (var entry : SettingsCatalog.FILES) {
            if (entry.filename().equals(FeatureSettings.FILE)) continue;
            ClientForm form;
            if (TerrainProfileForms.owns(entry.filename()) && terrain != null)
                form = terrain.forms().stream().filter(value -> value.route().equals(DocumentId.parse(entry.id()))).findFirst().orElseThrow();
            else if (entry.filename().endsWith(".toml")) form = nativePreferences.form(entry);
            else form = json.form(entry);
            forms.put(entry.filename(), form);
        }
        var groups = catalog(forms);
        var controlForm = keys.form(text("controls"));
        forms.put("controls", controlForm);
        groups.add(new ClientWorkflowLayout.Group(List.of(
                category("catalog/interface", "category.interface", "page_scope"),
                category("catalog/interface/skyweave_compat", "mod.skyweave_compat", "page_scope"),
                category("catalog/interface/skyweave_compat/controls", "controls", "controls_help")),
                List.of(placement(id("controls/keys"), controlForm, controlForm.title(), Component.empty(), Optional.empty(), Set.of())), List.of()));
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
                    var help = Component.translatable(entry.summaryKey());
                    var inactive = OptionalIntegrations.selection().inactive().get(fix.feature());
                    if (inactive != null) help = help.copy().append("\n").append(inactive);
                    placements.add(placement(id("features/" + fix.feature().key), forms.get(FeatureSettings.FILE),
                            Component.translatable(entry.labelKey()), help,
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
                                        Component.literal(field.spec().getComment() == null ? "" : field.spec().getComment()),
                                        Optional.of(NativeSettingsPolicy.path(field)), Set.of(NativeSettingsPolicy.timing(entry.timing(field.key()))))));
                    } else placements.add(placement(id("files/" + page.id() + "/" + entry.help()), form,
                            Component.translatable(entry.labelKey()), Component.translatable(entry.summaryKey()),
                            Optional.empty(), nativePreferences.fields(entry).isEmpty()
                                    ? Set.of(NativeSettingsPolicy.timing(entry.timing()))
                                    : nativePreferences.fields(entry).stream().map(field -> NativeSettingsPolicy.timing(field.timing(entry.filename())))
                                            .collect(java.util.stream.Collectors.toSet())));
                } else if (item instanceof SettingsCatalog.ShaderWorkspace && shaders != null) actions.add(shaders.action());
            }
            if (!placements.isEmpty() || !actions.isEmpty()) groups.add(new ClientWorkflowLayout.Group(List.of(
                    category("catalog/" + page.category().name().toLowerCase(Locale.ROOT),
                            "category." + page.category().name().toLowerCase(Locale.ROOT), "page_scope"),
                    category(prefix, "mod." + page.label(), "page_scope"),
                    category(prefix + "/" + section.kind().name().toLowerCase(Locale.ROOT), section.kind().label, "page_scope")),
                    placements, actions));
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
                            Optional.empty(), Set.of(ApplicationEffects.Timing.OWNER_PUBLICATION))), List.of()));
        }
        return ClientEditingWorkflow.open(parent, text("running_server"), forms, new ClientWorkflowLayout(groups, List.of())).screen();
    }

    private static <T> ClientForm policy(String filename, design.inherent.reform.definition.DocumentDefinition<T> definition) {
        var entry = SettingsCatalog.file(filename);
        return IntegratedSettings.form(Component.translatable(entry.labelKey()), DocumentId.parse(entry.id()), 2, definition,
                new ClientFormOptions<>(SettingsPresentation.json(design.inherent.skyweavecompat.configuration.JsonSettings.find(filename)), Map.of()));
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

    private final class ContextScreen extends Screen {
        private final Screen parent;
        private MultiLineLabel help;
        ContextScreen(Screen parent) { super(Component.translatable("skyweave_compat.configuration.title")); this.parent = parent; }
        @Override protected void init() {
            int span = Math.max(120, Math.min(310, width - 24));
            help = MultiLineLabel.create(font, text("context_choice_help"), Math.max(100, Math.min(480, width - 24)));
            int top = Math.max(49 + help.getLineCount() * font.lineHeight, height / 2 - 48);
            addRenderableWidget(Button.builder(text("local_files"), ignored -> minecraft.setScreen(local(this)))
                    .bounds((width - span) / 2, top, span, 20).tooltip(Tooltip.create(text("page_scope"))).build());
            var running = Button.builder(text("running_server"), ignored -> minecraft.setScreen(server(this)))
                    .bounds((width - span) / 2, top + 28, span, 20).tooltip(Tooltip.create(text("server_policy_scope"))).build();
            running.active = minecraft.getSingleplayerServer() != null && minecraft.player != null && minecraft.player.hasPermissions(2);
            addRenderableWidget(running);
            addRenderableWidget(Button.builder(Component.translatable("gui.done"), ignored -> onClose())
                    .bounds((width - span) / 2, top + 68, span, 20).build());
        }
        @Override public void onClose() { minecraft.setScreen(parent); }
        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            renderBackground(graphics, mouseX, mouseY, partialTick);
            graphics.drawCenteredString(font, title, width / 2, 20, 0xffffff);
            help.renderCentered(graphics, width / 2, 37, font.lineHeight, 0xb0b0b0);
            super.render(graphics, mouseX, mouseY, partialTick);
        }
    }
}
