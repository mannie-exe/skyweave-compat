package design.inherent.skyweavecompat.client;

import design.inherent.skyweavecompat.*;
import design.inherent.skyweavecompat.configuration.*;
import design.inherent.skyweavecompat.configuration.SettingsCatalog.Page;
import net.minecraft.client.gui.components.EditBox;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.EnumSet;
import design.inherent.skyweavecompat.configuration.SettingsCatalog.Category;
import design.inherent.skyweavecompat.configuration.SettingsCatalog.Group;
import design.inherent.skyweavecompat.configuration.StartupEdits;
import design.inherent.skyweavecompat.configuration.SectionNavigation;
import design.inherent.skyweavecompat.questlog.client.JournalPreferences;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Locale;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.config.ModConfigs;
import net.neoforged.fml.loading.FMLPaths;

/** Local preference editing and next-start configuration; no remote administration or live mixin reload. */
public final class ConfigurationOverview extends Screen {
    private final Screen parent;
    private StartupEdits draft;
    private Component feedback = Component.empty();
    private Component notice = Component.empty();
    private int noticeY, noticeWidth;
    private Feature detail;
    private SettingsPanel navigation, content;
    private Button save;
    private SettingsControls controls;
    private record ValidationJob(long after, java.util.function.Supplier<Component> validate) {}
    private final Map<String, ValidationJob> validationJobs = new HashMap<>();
    private net.minecraft.locale.Language language;
    private long resourceRevision;
    private float navigationScroll;
    private final Map<String, Float> scroll = new HashMap<>();
    private final Map<String, SettingsPanel.NavigationButton> links = new java.util.LinkedHashMap<>();
    private Page page = SettingsCatalog.page(Group.JOURNAL);
    private final Map<String, SectionNavigation.Bookmark> positions = new HashMap<>();
    private final Map<String, String> focused = new HashMap<>();
    private final EnumSet<Category> expanded = EnumSet.of(Category.INTERFACE);
    private EnumSet<Category> beforeSearch;
    private final Map<String, JsonEdits> json = new LinkedHashMap<>();
    private final Map<String, RegisteredEdits> toml = new LinkedHashMap<>();
    private final Map<String, String> unavailable = new HashMap<>();
    private String query = "";
    private EditBox search;
    private boolean searchPending;
    private boolean searchFocus;
    private String contentFocus, navigationFocus;
    private String selectedEntry;

    public ConfigurationOverview(ModContainer container, Screen parent) {
        super(Component.translatable("skyweave_compat.configuration.title")); this.parent = parent;
        load(); loadJson(); loadRegistered();
    }
    static net.minecraft.network.chat.MutableComponent text(String key, Object... args) { return Component.translatable("skyweave_compat.settings." + key, args); }
    private static Component featureName(Feature feature) { return Component.translatable(SettingsCatalog.entry(feature).labelKey()); }
    private static Component modName(Group group) { return text("mod." + group.label); }
    private static Component category(Category category) { return text("category." + category.name().toLowerCase(Locale.ROOT)); }
    private static Component on(boolean value) { return Component.translatable(value ? "options.on" : "options.off"); }
    private void load() {
        try { if (draft == null) draft = StartupEdits.open(ConfigLocations.ownedDirectory(FMLPaths.CONFIGDIR.get())); else draft.reload(); feedback = Component.empty(); }
        catch (Exception failure) { feedback = text("load_failed").append(" ").append(Component.literal(String.valueOf(failure.getMessage()))); }
    }
    private void loadJson() {
        for (var file : JsonSettings.FILES) try {
            var edits = json.get(file.filename());
            if (edits == null) json.put(file.filename(), new JsonEdits(ConfigLocations.directory(FMLPaths.CONFIGDIR.get(), file.filename()), file)); else edits.reload();
            unavailable.remove(file.filename());
        } catch (Exception failure) { unavailable.put(file.filename(), String.valueOf(failure.getMessage())); }
    }
    private boolean dirty() { return draft != null && draft.dirty() || json.values().stream().anyMatch(JsonEdits::dirty)
            || toml.values().stream().anyMatch(RegisteredEdits::dirty); }
    private void loadRegistered() {
        for (var entry : SettingsCatalog.FILES) {
            if (!entry.filename().endsWith(".toml")) continue;
            try {
                var config = registered(entry.filename());
                if (config == null) throw new java.io.IOException(text("registered_unavailable").getString());
                var edits = toml.get(entry.filename());
                if (edits == null) toml.put(entry.filename(), new RegisteredEdits(ConfigLocations.directory(FMLPaths.CONFIGDIR.get(), entry.filename()), entry.filename(),
                        (net.neoforged.neoforge.common.ModConfigSpec) config.getSpec()));
                else edits.reload();
                unavailable.remove(entry.filename());
            } catch (Exception failure) { unavailable.put(entry.filename(), String.valueOf(failure.getMessage())); }
        }
    }
    private String route() { return detail == null ? page == null ? "files" : page.id() : "detail/" + detail.key; }
    private void remember() {
        searchFocus = search != null && getFocused() == search;
        if (navigation != null) { navigationScroll = navigation.scroll(); navigationFocus = navigation.focusKey(); }
        if (content != null) {
            scroll.put(route(), content.scroll()); contentFocus = content.focusKey();
            positions.put(route(), content.bookmark()); focused.put(route(), contentFocus);
        }
    }
    private void navigate(Page target, String anchor) {
        remember(); detail = null; page = target;
        selectedEntry = anchor != null ? anchor : target == null ? "files" : target.id();
        if (target != null) { expanded.clear(); expanded.add(target.category()); }
        contentFocus = focused.get(route()); rebuildWidgets();
        content.jump(selectedEntry);
        updateSelection();
    }
    private void inspect(Feature feature) { remember(); selectedEntry = "feature/" + feature.key; detail = feature; contentFocus = null; rebuildWidgets(); }
    private void updateSelection() {
        String destination = selectedEntry == null ? route() : selectedEntry;
        String menu = links.containsKey(destination) ? destination : page == null ? "files" : page.id();
        links.forEach((id, link) -> link.selected(id.equals(menu)));
        content.highlight(selectedEntry);
    }
    private Button button(Component label, int width, Runnable action) {
        var button = Button.builder(label, b -> action.run()).bounds(0, 0, width, 20).build(); return button;
    }
    private Button valueButton(Component label, Component subject, int width, Runnable action) {
        return Button.builder(label, b -> action.run()).createNarration(supplier -> subject.copy().append(": ").append(supplier.get()))
                .bounds(0, 0, Math.max(width, font.width(label) + 12), 20).build();
    }
    @Override protected void init() {
        if (controls == null) controls = new SettingsControls(font);
        language = net.minecraft.locale.Language.getInstance(); resourceRevision = ClientResourceRevision.revision();
        int top = 34 + font.split(text("local"), Math.max(100, width - 24)).size() * font.lineHeight;
        notice = !feedback.getString().isEmpty() ? feedback : draft == null ? text("load_failed") : Component.empty();
        if (!notice.getString().isEmpty()) {
            noticeY = top; noticeWidth = Math.max(80, width - 128);
            int noticeHeight = Math.max(20, font.split(notice, noticeWidth).size() * font.lineHeight);
            var reload = button(text("reload"), 90, this::reload);
            reload.setPosition(width - 102, noticeY); addRenderableWidget(reload);
            top += noticeHeight + 8;
        }
        int paneHeight = Math.max(30, height - top - 38);
        int sidebar = Math.clamp(width / 4, 102, 170);
        if (search == null) {
            search = new EditBox(font, 10, top, sidebar, 20, text("search"));
            search.setMaxLength(128); search.setValue(query);
            search.setResponder(value -> {
                // Native cursor/selection changes also notify EditBox's responder.
                if (query.equals(value)) return;
                boolean wasEmpty = query.isBlank(); query = value;
                if (wasEmpty && !query.isBlank()) beforeSearch = expanded.clone();
                if (query.isBlank() && beforeSearch != null) {
                    expanded.clear(); expanded.addAll(beforeSearch);
                    if (page != null) expanded.add(page.category());
                    beforeSearch = null;
                }
                searchPending = true;
            });
        }
        search.setPosition(10, top); search.setWidth(sidebar); search.setMessage(text("search")); search.setHint(text("search"));
        addRenderableWidget(search);
        navigation = new SettingsPanel(minecraft, 10, top + 24, sidebar, Math.max(20, paneHeight - 24), true);
        content = new SettingsPanel(minecraft, sidebar + 18, top, Math.max(80, width - sidebar - 28), paneHeight, false);
        buildNavigation();
        navigation.finish(navigationScroll); addRenderableWidget(navigation);
        if (detail != null) buildDetails(detail);
        else if (page == null) buildStatus();
        else {
            content.section(page.id(), text("mod." + page.label()));
            content.row(category(page.category()), text("page_scope"));
            buildPage(page);
        }
        content.finish(scroll.getOrDefault(route(), 0f));
        content.restore(positions.get(route()));
        addRenderableWidget(content);
        navigation.restoreFocus(navigationFocus);
        content.restoreFocus(contentFocus);
        if (contentFocus != null && content.focusKey() != null) setFocused(content);
        else if (navigationFocus != null && navigation.focusKey() != null) setFocused(navigation);
        if (searchFocus) { setFocused(search); search.setFocused(true); }
        updateSelection();
        var back = button(Component.translatable("gui.back"), 80, this::onClose); back.setPosition(10, height - 28); addRenderableWidget(back);
        save = button(text("save"), Math.min(110, Math.max(80, width / 4)), this::saveDraft);
        save.setPosition(width - save.getWidth() - 10, height - 28); save.active = dirty(); addRenderableWidget(save);
    }
    private record Destination(String id, Component label, Component summary, boolean section) {
        Destination(String id, Component label, Component summary) { this(id, label, summary, false); }
    }
    private boolean preferencesAvailable() { return OptionalIntegrations.enabled(Feature.QUESTLOG_INTERFACE) || OptionalIntegrations.enabled(Feature.QUESTLOG_NOTIFICATIONS); }
    private List<SettingsCatalog.Item> items(SettingsCatalog.Section section) {
        return section.items().stream().filter(item -> !(item instanceof SettingsCatalog.Preference) || preferencesAvailable())
                .filter(item -> !(item instanceof SettingsCatalog.ShaderWorkspace) || OptionalIntegrations.enabled(Feature.SHADER_PATCHES)).toList();
    }
    private List<SettingsCatalog.Section> sections(Page target) {
        return target.sections().stream().filter(section -> !items(section).isEmpty()).toList();
    }
    private List<Page> pages(Category category) {
        var collator = java.text.Collator.getInstance(Locale.forLanguageTag(minecraft.getLanguageManager().getSelected().replace('_', '-')));
        return SettingsCatalog.pages(category).stream().sorted((a, b) -> {
            int order = collator.compare(text("mod." + a.label()).getString(), text("mod." + b.label()).getString());
            return order != 0 ? order : a.id().compareTo(b.id());
        }).toList();
    }
    private List<Destination> destinations(Page target) {
        var entries = new ArrayList<Destination>();
        for (var section : sections(target)) {
            entries.add(new Destination(section.id(), text(section.kind().label), Component.empty(), true));
            for (var item : items(section)) {
                if (item instanceof SettingsCatalog.Fix fix) entries.add(new Destination(item.id(), featureName(fix.feature()), Component.translatable(SettingsCatalog.entry(fix.feature()).summaryKey())));
                else if (item instanceof SettingsCatalog.Preference preference && preferencesAvailable()) entries.add(new Destination(item.id(),
                        Component.translatable("skyweave_compat.journal." + preference.key()), Component.translatable("skyweave_compat.journal." + preference.key() + "_description")));
                else if (item instanceof SettingsCatalog.File file) {
                    var definition = file.entry();
                    var inline = toml.get(definition.filename());
                    if (inline == null || inline.fields().size() != 1)
                        entries.add(new Destination(item.id(), Component.translatable(definition.labelKey()), Component.translatable(definition.summaryKey())));
                    var edits = json.get(definition.filename());
                    if (edits != null) for (String key : edits.keys()) entries.add(new Destination(definition.field(key).anchor(),
                            fieldName(edits, key), text("field." + edits.definition().help() + "." + key + ".help")));
                    var nativeEdits = toml.get(definition.filename());
                    if (nativeEdits != null) for (var field : nativeEdits.fields()) entries.add(new Destination(definition.field(field.key()).anchor(),
                            registeredName(definition.filename(), field), Component.literal(field.spec().getComment() == null ? field.key() : field.spec().getComment())));
                } else if (item instanceof SettingsCatalog.ShaderWorkspace) entries.add(new Destination(item.id(), text("shaders.rebuild"), text("shaders.rebuild_help")));
            }
        }
        return entries;
    }
    private List<Destination> visibleDestinations(Page target) {
        var all = destinations(target);
        if (query.isBlank()) return all;
        var visible = new ArrayList<Destination>();
        for (int start = 0; start < all.size();) {
            int end = start + 1;
            while (end < all.size() && !all.get(end).section()) end++;
            var header = all.get(start);
            var children = all.subList(start + 1, end).stream().filter(entry -> matches(target, entry)).toList();
            if (matches(target, header) || !children.isEmpty()) { visible.add(header); visible.addAll(children); }
            start = end;
        }
        return visible;
    }
    private void buildNavigation() {
        links.clear(); boolean searching = !query.isBlank(); int count = 0;
        for (Category category : Category.values()) {
            var pages = pages(category).stream().filter(target -> !searching || matches(target, null)
                    || destinations(target).stream().anyMatch(entry -> matches(target, entry))).toList();
            if (pages.isEmpty()) continue;
            var header = SettingsPanel.navigationButton(Component.literal(searching || expanded.contains(category) ? "− " : "+ ").append(category(category)), navigationWidth() - 20, true, false, () -> {
                remember(); if (!expanded.remove(category)) expanded.add(category); rebuildWidgets();
            });
            navigation.row("category/" + category, header.getMessage(), Component.empty(), header);
            if (!searching && !expanded.contains(category)) continue;
            for (var target : pages) {
                var link = SettingsPanel.navigationButton(text("mod." + target.label()), navigationWidth() - 20, false, false, () -> navigate(target, null));
                navigation.row(target.id(), link.getMessage(), Component.empty(), link); links.put(target.id(), link); count++;
                if (searching || detail == null && target.equals(page)) for (var entry : visibleDestinations(target)) {
                    var anchor = entry.section() ? SettingsPanel.sectionButton(entry.label(), navigationWidth() - 20, () -> navigate(target, entry.id()))
                            : SettingsPanel.subsectionButton(entry.label(), navigationWidth() - 20, () -> navigate(target, entry.id()));
                    navigation.row(entry.id(), entry.label(), Component.empty(), anchor); links.put(entry.id(), anchor);
                }
            }
        }
        if (searching && count == 0) navigation.row(text("no_results"), Component.empty());
        if (!searching) {
            var status = SettingsPanel.navigationButton(text("status_files"), navigationWidth() - 20, false, false, () -> navigate(null, null));
            navigation.row("files", status.getMessage(), Component.empty(), status); links.put("files", status);
        }
    }
    private boolean matches(Page target, Destination entry) {
        return SettingsSearch.matches(query, category(target.category()).getString(), text("mod." + target.label()).getString(),
                entry == null ? "" : entry.label().getString(), entry == null ? "" : entry.summary().getString(), entry == null ? target.id() : entry.id());
    }
    private static Component fieldName(JsonEdits edits, String key) { return text("field." + edits.definition().help() + "." + key); }
    private void buildPage(Page page) {
        for (var section : sections(page)) {
            content.beginGroup(section.id(), text(section.kind().label));
            for (var item : items(section)) {
                if (item instanceof SettingsCatalog.Fix fix) addFeature(fix.feature());
                else if (item instanceof SettingsCatalog.Preference preference) buildPreference(preference.key());
                else if (item instanceof SettingsCatalog.File file) addFile(file.entry());
                else if (item instanceof SettingsCatalog.ShaderWorkspace) buildShaders();
            }
            if (items(section).stream().anyMatch(item -> item instanceof SettingsCatalog.Preference))
                content.row(text("after_save.immediate"), Component.empty());
            content.endGroup();
        }
    }
    private void buildShaders() {
        for (var status : ShaderAssembly.states())
            content.row(Component.literal(status.id()), text("shaders." + status.state()).append(status.detail().isEmpty() ? "" : "\n" + status.detail()));
        content.anchorRow("shaders/rebuild", text("shaders.rebuild"), text("shaders.rebuild_help"), button(text("shaders.rebuild"), 85, () -> {
            ShaderAssembly.requestRebuild();
            try { net.irisshaders.iris.Iris.reload(); }
            catch (Exception failure) { feedback = text("change_failed").append(" ").append(String.valueOf(failure.getMessage())); }
            remember(); rebuildWidgets();
        }));
    }
    private void buildPreference(String key) {
        String setting = switch (key) {
            case "images" -> "itemImages";
            case "symbols" -> "conditionSymbols";
            case "notifications" -> "notifications";
            default -> throw new IllegalArgumentException("Unknown journal preference: " + key);
        };
        var edits = toml.get(JournalPreferences.FILE);
        Component label = Component.translatable("skyweave_compat.journal." + key);
        Component description = Component.translatable("skyweave_compat.journal." + key + "_description");
        if (edits == null) {
            content.anchorRow("preference/" + key, label, description.copy().append("\n").append(text("registered_unavailable")));
            return;
        }
        var field = edits.fields().stream().filter(candidate -> candidate.key().equals(setting)).findFirst().orElseThrow();
        var control = valueButton(registeredValue(field, edits.value(setting)), label, Math.max(65, Math.min(135, (width - navigationWidth()) / 2)), () -> {
            remember(); int current = field.choices().indexOf(edits.value(setting));
            edits.set(setting, field.choices().get((current + 1) % field.choices().size())); rebuildWidgets();
        });
        var reset = valueButton(text("reset"), label, 42, () -> { remember(); edits.reset(setting); rebuildWidgets(); });
        reset.setTooltip(Tooltip.create(text("default", edits.defaultValue(setting))));
        content.anchorRow("preference/" + key, label, description, control, reset);
    }
    private int navigationWidth() { return Math.clamp(width / 4, 102, 170); }
    private void addFeature(Feature feature) {
        var inspect = new SettingsInformationButton(featureName(feature), () -> inspect(feature));
        Component summary = Component.translatable(SettingsCatalog.entry(feature).summaryKey());
        if (!OptionalIntegrations.enabled(feature)) summary = summary.copy().append("\n").append(text("running", state(feature)));
        if (draft != null && draft.saved().enabled(feature) != OptionalIntegrations.configured(feature)) summary = summary.copy().append("\n").append(text("saved_next")).append(": ").append(on(draft.saved().enabled(feature)));
        if (draft != null && draft.edited(feature)) summary = summary.copy().append("\n").append(text("modified"));
        var toggle = valueButton(draft == null ? Component.literal("?") : on(draft.value(feature)), featureName(feature), 45, () -> {
            draft.set(feature, !draft.value(feature)); remember(); rebuildWidgets();
        });
        toggle.active = draft != null;
        toggle.setTooltip(Tooltip.create(featureName(feature).copy().append(". ").append(text("restart"))));
        content.anchorRow("feature/" + feature.key, featureName(feature), summary, toggle, inspect);
    }
    private Component state(Feature feature) {
        var selection = OptionalIntegrations.selection();
        if (selection.enabled(feature)) return text("active");
        var reason = selection.reasons().get(feature);
        if (reason == null) return text("disabled");
        return switch (reason.kind()) {
            case DISABLED -> text("disabled");
            case WRONG_SIDE -> text("wrong_side");
            case PREREQUISITE -> text("prerequisite", featureName(FeatureSelection.prerequisite(feature)));
            case MISSING -> text("missing", String.join(", ", reason.details().stream().map(this::dependencyName).toList()));
            case EXTERNAL -> text("external", String.join(", ", reason.details()));
        };
    }
    private String dependencyName(String id) {
        var mod = net.neoforged.fml.loading.LoadingModList.get().getMods().stream().filter(m -> m.getModId().equals(id)).findFirst();
        return mod.map(net.neoforged.neoforgespi.language.IModInfo::getDisplayName).orElseGet(() -> {
            String key = switch (id) {
                case "createredstonelinkgui" -> "redstone_link_gui"; case "create_redstone_additions" -> "redstone_additions";
                case "sky_archipelago" -> "sky_archipelago"; case "skyvillages" -> "sky_villages";
                case "aeronautics_delivery_quests" -> "delivery"; case "analogaudio" -> "analog_audio";
                case "aeronautics_utility_objects" -> "transmission_linkage"; case "linearbearing" -> "linear_bearing";
                case "distanthorizons" -> "distant_horizons"; case "sabledynlights" -> "lighting"; default -> id;
            };
            Component name = text("mod." + key); return name.getString().equals("skyweave_compat.settings.mod." + key) ? id : name.getString();
        });
    }
    private void buildDetails(Feature feature) {
        content.section("feature/" + feature.key, featureName(feature));
        content.row(Component.translatable(SettingsCatalog.entry(feature).summaryKey()), text("running", state(feature)));
        content.row(text("current_setting", on(OptionalIntegrations.configured(feature))), text(OptionalIntegrations.defaulted(feature) ? "omitted" : "explicit"));
        if (draft != null) {
            content.row(text("next_start", on(draft.saved().enabled(feature))), text(draft.saved().values().containsKey(feature) ? "explicit" : "omitted"));
            if (draft.edited(feature)) content.row(text("draft", on(draft.value(feature))), text("restart"));
        }
        content.row(text("default", on(feature.defaultEnabled)), Component.empty());
        content.row(text("scope", text(feature == Feature.BLUEMAP ? "dedicated" : FeatureSelection.clientOnly(feature) ? "client" : "both")), Component.empty());
        for (var entry : FeatureSelection.dependencies(feature).entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            String versions = entry.getValue().endsWith(".") ? entry.getValue() + "x" : entry.getValue();
            if (entry.getKey().equals("questlog")) versions += " / 3.5.3";
            content.row(text("requires", dependencyName(entry.getKey()) + " " + versions), Component.empty());
        }
        if (FeatureSelection.prerequisite(feature) != null) content.row(text("prerequisite", featureName(FeatureSelection.prerequisite(feature))), Component.empty());
        var copy = valueButton(text("copy_key"), featureName(feature), 78, () -> minecraft.keyboardHandler.setClipboard(feature.key));
        content.row(text("key", feature.key), Component.empty(), copy);
        addFile(SettingsCatalog.file(SettingsCatalog.entry(feature).filename()));
    }
    private void buildStatus() {
        content.section("files", text("status_files"));
        content.row(text("status_intro"), Component.empty());
        content.row(text("reload"), text("reload_summary"), button(text("reload"), 95, this::reload));
        folder();
        for (var file : SettingsCatalog.FILES) {
            if (file.group() == null) addFile(file);
            else content.row("file-link/" + file.id(), Component.literal(file.filename()),
                    text("section_title", category(file.group().category), modName(file.group())),
                    valueButton(text("view_section"), Component.literal(file.filename()), 90, () -> navigate(SettingsCatalog.page(file.group()), fileAnchor(file))), fileButton(file));
        }
    }
    private String fileAnchor(SettingsCatalog.FileEntry file) {
        if (!file.help().equals("journal")) {
            var edits = toml.get(file.filename());
            return edits != null && edits.fields().size() == 1 ? file.field(edits.fields().getFirst().key()).anchor() : "file/" + file.id();
        }
        var owner = SettingsCatalog.page(file.group());
        return sections(owner).stream().filter(section -> section.kind() == SettingsCatalog.SectionKind.PREFERENCES)
                .map(SettingsCatalog.Section::id).findFirst().orElse(owner.id());
    }
    private Button fileButton(SettingsCatalog.FileEntry file) {
        var path = ConfigLocations.file(FMLPaths.CONFIGDIR.get(), file.filename());
        var open = valueButton(text("open_file"), Component.literal(file.filename()), 70, () -> Util.getPlatform().openFile(path.toFile()));
        open.active = Files.isRegularFile(path); return open;
    }
    private void addFile(SettingsCatalog.FileEntry file) {
        var path = ConfigLocations.file(FMLPaths.CONFIGDIR.get(), file.filename()); boolean exists = Files.isRegularFile(path);
        var open = fileButton(file);
        Component help = Component.translatable(file.summaryKey()).copy().append("\n").append(text("timing." + file.timing().name().toLowerCase(Locale.ROOT)));
        if (!exists) help = help.copy().append("\n").append(text("not_created"));
        Component label = file.group() == null || detail != null ? Component.literal(file.filename()) : Component.translatable(file.labelKey());
        var edits = json.get(file.filename());
        if (JsonSettings.find(file.filename()) != null && detail == null) {
            content.anchorRow("file/" + file.id(), label, help);
            if (unavailable.containsKey(file.filename())) {
                content.row(text("load_failed"), Component.literal(unavailable.get(file.filename())).append(edits == null ? Component.empty() : text("retained_snapshot")));
                if (edits == null) return;
            }
            if (edits != null) buildJson(edits);
        } else if (file.filename().endsWith(".toml") && detail == null) {
            var nativeEdits = toml.get(file.filename());
            if (nativeEdits == null || nativeEdits.fields().size() != 1)
                content.anchorRow("file/" + file.id(), label, Component.translatable(file.summaryKey()));
            if (unavailable.containsKey(file.filename())) content.row(text("load_failed"), Component.literal(unavailable.get(file.filename())));
            if (nativeEdits != null) buildRegistered(nativeEdits);
        } else content.anchorRow("file/" + file.id(), label, help, open);
    }
    private static ModConfig registered(String filename) {
        return java.util.stream.Stream.of(ModConfig.Type.CLIENT, ModConfig.Type.COMMON).flatMap(type -> ModConfigs.getConfigSet(type).stream())
                .filter(config -> config.getFileName().equals(ConfigLocations.registeredName(filename)) && config.getLoadedConfig() != null
                        && config.getSpec() instanceof net.neoforged.neoforge.common.ModConfigSpec
                        && config.getFullPath().toAbsolutePath().normalize().equals(ConfigLocations.file(FMLPaths.CONFIGDIR.get(), filename).toAbsolutePath().normalize()))
                .findFirst().orElse(null);
    }
    private static Component registeredName(String filename, RegisteredEdits.Field field) {
        String translation = field.spec().getTranslationKey();
        if (translation == null) {
            var config = registered(filename);
            if (config == null) return Component.literal(registeredFallback(field.binding().getPath().getLast()));
            translation = config.getModId() + ".configuration." + field.key();
        }
        return Component.translatableWithFallback(translation, registeredFallback(field.binding().getPath().getLast()));
    }
    private static String registeredFallback(String key) {
        String words = key.replaceAll("([a-z0-9])([A-Z])", "$1 $2").replace('_', ' ');
        return words.isEmpty() ? key : Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }
    private Component registeredValue(RegisteredEdits.Field field, String value) {
        if (field.defaultValue() instanceof Boolean) return on(Boolean.parseBoolean(value));
        if (field.defaultValue() instanceof net.neoforged.neoforge.common.TranslatableEnum) {
            for (Object choice : ((Enum<?>) field.defaultValue()).getDeclaringClass().getEnumConstants())
                if (((Enum<?>) choice).name().equals(value)) return ((net.neoforged.neoforge.common.TranslatableEnum) choice).getTranslatedName();
        }
        return Component.literal(value);
    }
    private void buildRegistered(RegisteredEdits edits) {
        String file = edits.filename();
        content.row("validation/" + file, Component.empty(), validation(edits));
        String lastGroup = "";
        for (var field : edits.fields()) {
            var path = field.binding().getPath();
            String group = String.join(" / ", path.subList(0, path.size() - 1));
            if (!group.isEmpty() && !group.equals(lastGroup)) {
                content.heading(Component.translatableWithFallback("skyweave_compat.settings.registered_group." + group, group));
                lastGroup = group;
            }
            String key = field.key(), id = SettingsCatalog.field(file, key).anchor();
            Component label = registeredName(file, field);
            String description = field.spec().getComment();
            var timing = switch (field.spec().restartType()) {
                case NONE -> SettingsCatalog.Timing.IMMEDIATE;
                case WORLD -> SettingsCatalog.Timing.SERVER_START;
                case GAME -> SettingsCatalog.Timing.GAME_RESTART;
            };
            if (SettingsCatalog.timing(file, key) == SettingsCatalog.Timing.NEW_WORLD) timing = SettingsCatalog.Timing.NEW_WORLD;
            Component help = Component.literal(description == null ? "" : description.lines().findFirst().orElse(""))
                    .append("\n").append(text("after_save." + timing.name().toLowerCase(Locale.ROOT)));
            if (field.spec().getRange() != null) help = help.copy().append("\n").append(text("bounds", field.spec().getRange().toString()));
            var reset = valueButton(text("reset"), label, 42, () -> { remember(); edits.reset(key); rebuildWidgets(); });
            reset.setTooltip(Tooltip.create(text("default", edits.defaultValue(key))));
            if (field.choices().isEmpty()) {
                content.relatedField(id, relationship(file, key, field.definition()), label, help, controls.field(label, edits.binding(key),
                        () -> validateLater(file, () -> validation(edits)))
                        .toArray(net.minecraft.client.gui.components.AbstractWidget[]::new));
            } else {
                var control = valueButton(registeredValue(field, edits.value(key)), label, Math.clamp(font.width(registeredValue(field, edits.value(key))) + 12, 65, 130), () -> {
                    remember(); int current = field.choices().indexOf(edits.value(key));
                    edits.set(key, field.choices().get((current + 1) % field.choices().size())); rebuildWidgets();
                });
                if (description != null) control.setTooltip(Tooltip.create(Component.literal(description)));
                content.relatedField(id, relationship(file, key, field.definition()), label, help, control, reset);
            }
        }
    }
    private Component validation(RegisteredEdits edits) {
        try { edits.candidate(); return edits.nativeMatchesFile()?Component.empty():
                text("native_reload_pending").withStyle(net.minecraft.ChatFormatting.YELLOW); }
        catch (Exception invalid) { return failure("invalid", invalid).withStyle(net.minecraft.ChatFormatting.RED); }
    }
    private void buildJson(JsonEdits edits) {
        String file = edits.definition().filename();
        content.row("validation/" + file, Component.empty(), validation(edits));
        String group = "";
        for (String key : edits.keys()) {
            String nextGroup = key.contains(".") ? key.substring(0, key.indexOf('.')) : "";
            if (!nextGroup.equals(group) && !nextGroup.isEmpty())
                content.section("json-group/" + SettingsCatalog.file(file).id() + "/" + nextGroup,
                        text("field." + edits.definition().help() + "." + nextGroup));
            group = nextGroup;
            String id = SettingsCatalog.field(file, key).anchor();
            Component label = fieldName(edits, key);
            Component help = text("field." + edits.definition().help() + "." + key + ".help");
            switch (edits.kind(key)) {
                case NUMBER, STRING -> {
                    if (edits.field(key) != null) {
                        content.relatedField(id, relationship(file, key, edits.field(key)), label, withUnits(help, edits.field(key)), controls.field(label, edits.binding(key),
                                () -> validateLater(file, () -> validation(edits)))
                                .toArray(net.minecraft.client.gui.components.AbstractWidget[]::new));
                        break;
                    }
                    var input = new EditBox(font, 0, 0, 100, 20, label); input.setMaxLength(edits.kind(key) == JsonEdits.Kind.STRING ? 256 : 64); input.setValue(edits.value(key));
                    input.setResponder(value -> {
                        if (edits.value(key).equals(value)) return;
                        edits.set(key, value);
                        try { edits.candidate(); input.setTextColor(SettingsPanel.TITLE_TEXT); input.setTooltip(null); }
                        catch (Exception invalid) { input.setTextColor(net.minecraft.ChatFormatting.RED.getColor()); input.setTooltip(Tooltip.create(failure("invalid", invalid))); }
                        content.summary("validation/" + file, validation(edits));
                    });
                    var reset = valueButton(text("reset"), label, 42, () -> { edits.reset(key); input.setValue(edits.value(key)); });
                    reset.setTooltip(Tooltip.create(text("default", edits.defaultValue(key))));
                    content.field(id, label, help, input, reset);
                }
                case BOOLEAN -> content.field(id, label, help, valueButton(on(Boolean.parseBoolean(edits.value(key))), label, 65, () -> {
                    remember(); edits.set(key, Boolean.toString(!Boolean.parseBoolean(edits.value(key)))); rebuildWidgets();
                }), valueButton(text("reset"), label, 42, () -> { remember(); edits.reset(key); rebuildWidgets(); }));
                case STRINGS -> {
                    content.section(id, label); content.row(Component.empty(), help);
                    var values = new ArrayList<>(edits.strings(key));
                    int listWidth = Math.max(40, content.fieldWidth() - Math.max(55, font.width(text("remove")) + 12) - 4);
                    for (int i = 0; i < values.size(); i++) {
                        final int index = i;
                        var input = new EditBox(font, 0, 0, listWidth, 20, label.copy().append(" " + (i + 1)));
                        input.setMaxLength(256); input.setValue(values.get(i)); input.setResponder(value -> {
                            if (values.get(index).equals(value)) return;
                            values.set(index, value); edits.strings(key, values); content.summary("validation/" + file, validation(edits));
                        });
                        content.field(id + "/" + i, Component.empty(), Component.empty(), input, valueButton(text("remove"), label, 55, () -> {
                            remember(); values.remove(index); edits.strings(key, values); rebuildWidgets();
                        }));
                    }
                    var add = valueButton(text("add"), label, 65, () -> { remember(); values.add(""); edits.strings(key, values); rebuildWidgets(); });
                    var field = edits.field(key);
                    add.active = field == null || field.maximumEntries() < 0 || values.size() < field.maximumEntries();
                    content.row(Component.empty(), Component.empty(), add, valueButton(text("reset"), label, 42, () -> {
                        remember(); edits.reset(key); rebuildWidgets();
                    }));
                }
            }
        }
    }
    private Component validation(JsonEdits edits) {
        try { edits.candidate(); return Component.empty(); }
        catch (Exception failure) { return failure("invalid", failure).withStyle(net.minecraft.ChatFormatting.RED); }
    }
    private void validateLater(String file, java.util.function.Supplier<Component> validate) {
        content.summary("validation/" + file, text("validating"));
        validationJobs.put(file, new ValidationJob(Util.getMillis() + 150, validate));
    }
    private static String relationship(String file, String key, SettingDefinition definition) {
        if (definition == null) return null;
        String parent = key.contains(".") ? key.substring(0, key.lastIndexOf('.')) : "";
        String group = definition.group().isEmpty() ? parent : parent + "/" + definition.group();
        return group.isEmpty() ? null : file + "/" + group;
    }
    private static Component withUnits(Component help, SettingDefinition definition) {
        if (definition.unit().isEmpty()) return help;
        return help.copy().append("\n").append(text("unit." + definition.unit()));
    }
    private static net.minecraft.network.chat.MutableComponent failure(String key, Exception failure) {
        return text(key).append(" ").append(Component.literal(String.valueOf(failure.getMessage())));
    }
    private void folder() { content.row(Component.empty(), Component.empty(), button(text("open_folder"), 115, () -> Util.getPlatform().openFile(ConfigLocations.ownedDirectory(FMLPaths.CONFIGDIR.get()).toFile()))); }

    private void saveDraft() {
        if (!dirty()) return;
        boolean generation = draft != null && draft.changes().keySet().stream().anyMatch(feature -> SettingsCatalog.group(feature).category == Category.WORLD)
                || json.values().stream().anyMatch(edits -> SettingsCatalog.changesGeneration(edits.definition().filename(), edits.changedKeys()))
                || toml.values().stream().anyMatch(edits -> edits.changedKeys().stream()
                        .anyMatch(key -> SettingsCatalog.timing(edits.filename(), key) == SettingsCatalog.Timing.NEW_WORLD));
        // Registered COMMON values update their owner immediately. Do not change
        // authoritative generation beneath a running integrated server.
        if (minecraft.getSingleplayerServer() != null && toml.values().stream()
                .anyMatch(edits -> edits.changedKeys().stream()
                        .anyMatch(key -> SettingsCatalog.timing(edits.filename(), key) == SettingsCatalog.Timing.NEW_WORLD))) {
            feedback = text("generation_leave_world");
            rebuildWidgets();
            return;
        }
        if (generation) {
            remember(); minecraft.setScreen(new ConfirmScreen(answer -> { minecraft.setScreen(this); if (answer) writeDraft(); }, text("generation_title"), text("generation_message")));
        } else writeDraft();
    }
    private void writeDraft() {
        remember();
        try {
            if (draft != null && draft.dirty()) {
                var proposed = new java.util.EnumMap<Feature, Boolean>(Feature.class); proposed.putAll(draft.saved().values()); proposed.putAll(draft.changes());
                var versions = new LinkedHashMap<String, String>();
                net.neoforged.fml.loading.LoadingModList.get().getMods().forEach(mod -> versions.put(mod.getModId(), mod.getVersion().toString()));
                FeatureSelection.select(new FeatureSettings(proposed), versions, true);
            }
            var writes = new ArrayList<SettingsCommit.Write>();
            if (draft != null && draft.dirty()) writes.add(draft.prepare());
            for (var edits : json.values()) if (edits.dirty()) writes.add(edits.prepare());
            for (var edits : toml.values()) if (edits.dirty()) writes.add(edits.prepare());
            var result = ProfileEditPlan.prepare(ConfigLocations.ownedDirectory(FMLPaths.CONFIGDIR.get()), writes).save();
            if (result.success()) {
                feedback = text("saved_files", String.join(", ", result.written()));
                for (var edits : toml.values()) if (edits.warning() != null) feedback = feedback.copy().append("\n").append(Component.literal(edits.warning()));
            }
            else feedback = text("save_partial", result.written().isEmpty() ? text("none") : String.join(", ", result.written()), result.failed())
                    .append("\n").append(Component.literal(String.valueOf(result.failure().getMessage())));
        } catch (Exception failure) { feedback = failure("save_failed", failure); }
        rebuildWidgets();
    }
    private void reload() {
        remember(); Runnable reload = () -> { load(); loadJson(); loadRegistered(); rebuildWidgets(); };
        if (dirty()) minecraft.setScreen(new ConfirmScreen(answer -> { minecraft.setScreen(this); if (answer) reload.run(); }, text("reload_title"), text("reload_message"), text("reload"), text("keep_editing")));
        else reload.run();
    }
    @Override public void tick() {
        long now = Util.getMillis();
        var ready = validationJobs.entrySet().stream().filter(entry -> entry.getValue().after <= now).toList();
        for (var entry : ready) {
            validationJobs.remove(entry.getKey());
            content.summary("validation/" + entry.getKey(), entry.getValue().validate.get());
        }
        if (searchPending) {
            searchPending = false;
            // Filtering replaces only the result tree. Native caret/selection/clipboard state stays in EditBox.
            var previous = navigation;
            navigation = new SettingsPanel(minecraft, 10, search.getY() + 24, search.getWidth(),
                    Math.max(20, height - search.getY() - 62), true);
            removeWidget(previous); buildNavigation(); navigation.finish(0); addRenderableWidget(navigation);
            children().sort(java.util.Comparator.comparingInt(child -> child == search ? 0 : child == navigation ? 1 : child == content ? 2 : 3));
            if (getFocused() == previous) { setFocused(navigation); navigation.focusVisibleControl(); }
            updateSelection();
        }
        if (language != net.minecraft.locale.Language.getInstance() || resourceRevision != ClientResourceRevision.revision()) {
            remember(); rebuildWidgets();
        }
    }
    @Override protected void setInitialFocus() {
        if (getFocused() == null || !children().contains(getFocused())) super.setInitialFocus();
    }
    @Override public void resize(Minecraft client, int width, int height) { remember(); super.resize(client, width, height); }
    @Override public boolean mouseClicked(double x, double y, int button) {
        boolean handled = super.mouseClicked(x, y, button);
        // Native dispatch assigns focus after the callback, which may have replaced its widgets.
        var focused = getFocused();
        if (minecraft.screen == this && focused != null && !children().contains(focused)) {
            if (focused instanceof SettingsPanel previous) {
                String key = previous.focusKey();
                if (detail != null && !previous.isNavigation()) this.focused.put(page == null ? "files" : page.id(), key);
                var replacement = previous.isNavigation() ? navigation : content;
                setFocused(replacement); replacement.restoreFocus(key);
            } else if (focused instanceof net.minecraft.client.gui.components.AbstractWidget previous) {
                var replacement = children().stream().filter(child -> child instanceof net.minecraft.client.gui.components.AbstractWidget widget
                        && widget.getMessage().equals(previous.getMessage())).findFirst();
                if (replacement.isPresent()) setFocused(replacement.get());
                else {
                    setFocused(content); content.restoreFocus(contentFocus);
                    if (content.getFocused() == null) content.focusVisibleControl();
                }
            }
        }
        return handled;
    }
    @Override public void onClose() {
        if (detail != null) {
            remember(); detail = null; contentFocus = focused.get(route()); rebuildWidgets(); return;
        }
        remember();
        if (dirty()) minecraft.setScreen(new ConfirmScreen(answer -> { if (answer) { if (draft != null) draft.discard(); json.values().forEach(JsonEdits::discard); toml.values().forEach(RegisteredEdits::discard); minecraft.setScreen(parent); } else minecraft.setScreen(this); }, text("discard_title"), text("discard_message"), text("discard"), text("keep_editing")));
        else minecraft.setScreen(parent);
    }
    @Override public void render(GuiGraphics graphics, int mx, int my, float tick) {
        updateSelection();
        renderBackground(graphics, mx, my, tick); save.active = dirty(); super.render(graphics, mx, my, tick);
        graphics.drawString(font, title, 12, 10, 0xffffff);
        graphics.drawWordWrap(font, text("local"), 12, 24, Math.max(100, width - 24), 0xc0c0c0);
        if (!notice.getString().isEmpty()) graphics.drawWordWrap(font, notice, 12, noticeY + 2, noticeWidth, 0xffffff);
        int available = Math.max(0, width - save.getWidth() - 112);
        if (available > 90) {
            Component hint = text(dirty() ? "save_scope" : "restart");
            int textHeight = font.split(hint, available).size() * font.lineHeight;
            int textY = save.getY() + (save.getHeight() - textHeight) / 2 + 1;
            graphics.drawWordWrap(font, hint, 100, textY, available, 0xc0c0c0);
        }
    }
}
