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
    private Button save, revert, review, viewButton;
    private SettingsControls controls;
    private final ServerSettingsView serverSettings;
    private List<Component> saveReport = List.of();
    private record FocusRequest(SettingsPanel panel, String key) {}
    private FocusRequest requestedFocus;
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
    private enum View { ALL, MODIFIED, INVALID, RESTART }
    private View view = View.ALL;
    private record ViewState(boolean modified, boolean invalid, boolean restart) {
        boolean accepts(View view) { return switch (view) { case ALL -> true; case MODIFIED -> modified; case INVALID -> invalid; case RESTART -> restart; }; }
    }
    private final Map<String,ViewState> viewStates = new HashMap<>();
    private record FilterPosition(Page page, Feature detail, String selected, EnumSet<Category> expanded,
            Map<String,SectionNavigation.Bookmark> positions, Map<String,Float> scroll, Map<String,String> focused,
            float navigationScroll, String navigationFocus, String contentFocus) {}
    private FilterPosition beforeFilter;
    private boolean restoredFilterPosition;
    private java.util.Set<String> shown = java.util.Set.of();
    private final java.util.Set<String> collapsed = new java.util.HashSet<>();
    private final Map<String, JsonEdits> json = new LinkedHashMap<>();
    private final Map<String, RegisteredEdits> toml = new LinkedHashMap<>();
    private final Map<String, String> unavailable = new HashMap<>();
    private String query = "";
    private EditBox search;
    private boolean searchPending, resultSetChanged;
    private boolean searchFocus;
    private String contentFocus, navigationFocus;
    private String selectedEntry;

    public ConfigurationOverview(ModContainer container, Screen parent) {
        super(Component.translatable("skyweave_compat.configuration.title")); this.parent = parent;
        serverSettings = new ServerSettingsView(Minecraft.getInstance(), this::serverChanged);
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
    private boolean dirty() { if (serverSettings.selected()) return json.values().stream().filter(edits -> ServerSettingsContext.Document.supports(edits.definition().filename())).anyMatch(JsonEdits::dirty);
        return draft != null && draft.dirty() || json.values().stream().anyMatch(JsonEdits::dirty)
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
        remember(); detail = null; page = target; expandDestination(anchor);
        selectedEntry = anchor != null ? anchor : target == null ? "files" : target.id();
        if (target != null) { expanded.clear(); expanded.add(target.category()); }
        contentFocus = focused.get(route()); rebuildWidgets();
        content.jump(selectedEntry);
        updateSelection();
    }
    private static String jsonGroup(JsonEdits edits, String key) {
        return key.contains(".") ? "json-group/" + SettingsCatalog.file(edits.definition().filename()).id() + "/" + key.substring(0,key.indexOf('.')) : null;
    }
    private void expandDestination(String anchor) {
        if (anchor == null) return;
        for (var edits : json.values()) for (String key : edits.keys())
            if (SettingsCatalog.field(edits.definition().filename(),key).anchor().equals(anchor)) collapsed.remove(jsonGroup(edits,key));
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
        int top = 34 + font.split(contextIntro(), Math.max(100, width - 24)).size() * font.lineHeight;
        var contextButton = button(text(serverSettings.selected() ? "context_server" : "context_local"),
                Math.max(80, Math.min(width - 20, 260)), this::switchContext);
        contextButton.active = !serverSettings.busy() && (serverSettings.selected() || serverSettings.available());
        contextButton.setTooltip(Tooltip.create(text("context_help")));
        contextButton.setPosition(10, top); addRenderableWidget(contextButton);
        if (serverSettings.selected() && serverSettings.retryable()) {
            var retry = button(text("application_retry"), Math.max(80, Math.min(120,width - contextButton.getWidth() - 24)), serverSettings::retry);
            retry.active = serverSettings.ready() && !dirty(); retry.setPosition(contextButton.getX()+contextButton.getWidth()+4,top);
            if (retry.getX()+retry.getWidth()>width-10) { top+=24; retry.setPosition(10,top); }
            addRenderableWidget(retry);
        }
        top += 24;
        notice = !feedback.getString().isEmpty() ? feedback : draft == null ? text("load_failed") : Component.empty();
        if (!notice.getString().isEmpty()) {
            noticeY = top; noticeWidth = Math.max(80, width - 128);
            int noticeHeight = Math.max(20, font.split(notice, noticeWidth).size() * font.lineHeight);
            var reload = button(text("reload"), 90, this::reload);
            reload.setPosition(width - 102, noticeY); addRenderableWidget(reload);
            top += noticeHeight + 8;
        }
        int paneHeight = Math.max(30, height - top - 62);
        int sidebar = Math.clamp(width / 4, 102, 170);
        if (search == null) {
            search = new EditBox(font, 10, top, sidebar, 20, text("search"));
            search.setMaxLength(128); search.setValue(query);
            search.setResponder(value -> {
                // Native cursor/selection changes also notify EditBox's responder.
                if (query.equals(value)) return;
                boolean wasFiltering = filtering();
                query = value; filterChanged(wasFiltering); searchPending = true; resultSetChanged=true;
            });
        }
        search.setPosition(10, top); search.setWidth(sidebar); search.setMessage(text("search")); search.setHint(text("search"));
        addRenderableWidget(search);
        viewButton = button(text("filter." + view.name().toLowerCase(Locale.ROOT)), sidebar, this::nextView);
        viewButton.setPosition(10,top+24); viewButton.setTooltip(Tooltip.create(text("filter_help"))); addRenderableWidget(viewButton);
        navigation = new SettingsPanel(minecraft, 10, top + 48, sidebar, Math.max(20, paneHeight - 48), true);
        content = new SettingsPanel(minecraft, sidebar + 18, top, Math.max(80, width - sidebar - 28), paneHeight, false);
        buildNavigation();
        navigation.finish(navigationScroll); addRenderableWidget(navigation);
        if (detail != null) buildDetails(detail);
        else if (page == null) buildStatus();
        else {
            content.section(page.id(), text("mod." + page.label()));
            content.row(category(page.category()), text(serverSettings.selected() ? "server_policy_scope" : "page_scope"));
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
        revert = button(text("revert"), Math.max(80, Math.min(110, width / 3)), this::revertDraft);
        revert.setPosition(10, height - 52); revert.active = dirty() && !serverSettings.busy(); addRenderableWidget(revert);
        review = button(text("review"), Math.max(100, Math.min(150, width / 2 - 16)), this::reviewDraft);
        review.setPosition(width - review.getWidth() - 10, height - 52); review.active = dirty() && !serverSettings.busy(); addRenderableWidget(review);
        var back = button(Component.translatable("gui.back"), 80, this::onClose); back.setPosition(10, height - 28); addRenderableWidget(back);
        save = button(text("save"), Math.min(110, Math.max(80, width / 4)), this::saveDraft);
        save.setPosition(width - save.getWidth() - 10, height - 28); save.active = canSave();
        save.setTooltip(Tooltip.create(text(serverSettings.selected() ? "server_policy_timing" : "save_scope"))); addRenderableWidget(save);
    }
    private record Destination(String id, Component label, Component summary, boolean section) {
        Destination(String id, Component label, Component summary) { this(id, label, summary, false); }
    }
    private boolean preferencesAvailable() { return OptionalIntegrations.enabled(Feature.QUESTLOG_INTERFACE) || OptionalIntegrations.enabled(Feature.QUESTLOG_NOTIFICATIONS); }
    private List<SettingsCatalog.Item> items(SettingsCatalog.Section section) {
        return section.items().stream().filter(item -> !serverSettings.selected() || item instanceof SettingsCatalog.File file && ServerSettingsContext.Document.supports(file.entry().filename())).filter(item -> !(item instanceof SettingsCatalog.Preference) || preferencesAvailable())
                .filter(item -> !(item instanceof SettingsCatalog.ShaderWorkspace) || OptionalIntegrations.enabled(Feature.SHADER_PATCHES)).toList();
    }
    private List<SettingsCatalog.Section> sections(Page target) {
        return target.sections().stream().filter(section -> !items(section).isEmpty()).toList();
    }
    private List<Page> pages(Category category) {
        var collator = java.text.Collator.getInstance(Locale.forLanguageTag(minecraft.getLanguageManager().getSelected().replace('_', '-')));
        return SettingsCatalog.pages(category).stream().filter(target -> !serverSettings.selected() || !sections(target).isEmpty()).sorted((a, b) -> {
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
                        entries.add(new Destination(item.id(), Component.translatable(definition.labelKey()), serverSettings.selected() ? text("server_file_policy") : Component.translatable(definition.summaryKey())));
                    var edits = json.get(definition.filename());
                    if (edits != null) for (String key : edits.keys()) entries.add(new Destination(definition.field(key).anchor(),
                            fieldName(edits, key), text("field." + edits.definition().help() + "." + key + ".help")));
                    var nativeEdits = toml.get(definition.filename());
                    if (nativeEdits != null) for (var field : nativeEdits.fields()) if (presentedField(definition, nativeEdits, target, field.key())) entries.add(new Destination(definition.field(field.key()).anchor(),
                            registeredName(definition.filename(), field), Component.literal(field.spec().getComment() == null ? field.key() : field.spec().getComment())));
                } else if (item instanceof SettingsCatalog.ShaderWorkspace) entries.add(new Destination(item.id(), text("shaders.rebuild"), text("shaders.rebuild_help")));
            }
        }
        return entries;
    }
    private List<Destination> visibleDestinations(Page target) {
        var all = destinations(target);
        if (!filtering()) return all;
        var visible = new ArrayList<Destination>();
        for (int start = 0; start < all.size();) {
            int end = start + 1;
            while (end < all.size() && !all.get(end).section()) end++;
            var header = all.get(start);
            boolean groupMatch = matches(target, header);
            var children = all.subList(start + 1, end).stream().filter(entry -> accepts(entry.id()) && (groupMatch || matches(target, entry))).toList();
            if (!children.isEmpty()) { visible.add(header); visible.addAll(children); }
            start = end;
        }
        return visible;
    }
    private void buildNavigation() {
        refreshViewStates();
        links.clear(); boolean searching = filtering(); int count = 0;
        for (Category category : Category.values()) {
            var pages = pages(category).stream().filter(target -> !searching || !visibleDestinations(target).isEmpty()).toList();
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
    private boolean filtering() { return !query.isBlank() || view != View.ALL; }
    private void filterChanged(boolean wasFiltering) {
        if (!wasFiltering && filtering()) {
            remember(); beforeFilter = new FilterPosition(page,detail,selectedEntry,expanded.clone(),Map.copyOf(positions),
                    Map.copyOf(scroll),new HashMap<>(focused),navigationScroll,navigationFocus,contentFocus);
        } else if (wasFiltering && !filtering() && beforeFilter != null) {
            var saved = beforeFilter; beforeFilter = null;
            page=saved.page(); detail=saved.detail(); selectedEntry=saved.selected(); expanded.clear(); expanded.addAll(saved.expanded());
            positions.clear(); positions.putAll(saved.positions()); scroll.clear(); scroll.putAll(saved.scroll());
            focused.clear(); focused.putAll(saved.focused()); navigationScroll=saved.navigationScroll();
            navigationFocus=saved.navigationFocus(); contentFocus=saved.contentFocus(); restoredFilterPosition=true;
        }
    }
    private void rebuildFiltered(boolean newResults) {
        if (!restoredFilterPosition) remember();
        else searchFocus = search != null && getFocused() == search;
        if (newResults && filtering()) { navigationScroll=0; navigationFocus=null; }
        restoredFilterPosition=false; rebuildWidgets();
    }
    private void nextView() {
        boolean wasFiltering = filtering(); view = View.values()[(view.ordinal()+1)%View.values().length];
        if (view == View.INVALID) {
            json.values().forEach(edits -> controls.feedback(SettingsCatalog.file(edits.definition().filename()).id(),edits.issues()));
            toml.values().forEach(edits -> controls.feedback(SettingsCatalog.file(edits.filename()).id(),edits.issues()));
        }
        filterChanged(wasFiltering); rebuildFiltered(true); setFocused(viewButton);
    }
    private boolean accepts(String anchor) { return view == View.ALL || viewStates.getOrDefault(anchor,new ViewState(false,false,false)).accepts(view); }
    private static boolean restart(SettingsCatalog.Timing timing) {
        return timing == SettingsCatalog.Timing.GAME_RESTART || timing == SettingsCatalog.Timing.SERVER_START || timing == SettingsCatalog.Timing.NEW_WORLD;
    }
    private void refreshViewStates() {
        viewStates.clear();
        if (draft != null) for (Feature feature : Feature.values())
            viewStates.put("feature/"+feature.key,new ViewState(draft.edited(feature),false,true));
        for (var file : SettingsCatalog.FILES) {
            var owned = json.get(file.filename()); var nativeEdits = toml.get(file.filename());
            var states = new ArrayList<ViewState>();
            if (owned != null) for (String key : owned.keys()) {
                var id = file.field(key); var definition = owned.field(key);
                var state = new ViewState(owned.edited(key), controls.hasIssue(id)
                        || definition != null && definition.inspect(owned.value(key)).issue()!=null,restart(file.timing(key)));
                states.add(state); viewStates.put(id.anchor(),state);
            }
            if (nativeEdits != null) for (var field : nativeEdits.fields()) {
                var id = file.field(field.key());
                var state = new ViewState(nativeEdits.changedKeys().contains(field.key()), controls.hasIssue(id)
                        || nativeEdits.binding(field.key()).issue()!=null,restart(field.timing(file.filename())));
                states.add(state); viewStates.put(id.anchor(),state);
                if (file.filename().equals(JournalPreferences.FILE)) {
                    String preference = switch(field.key()) { case "itemImages" -> "images"; case "conditionSymbols" -> "symbols"; case "notifications" -> "notifications"; default -> null; };
                    if (preference!=null) viewStates.put("preference/"+preference,state);
                }
            }
            viewStates.put("file/"+file.id(),new ViewState(states.stream().anyMatch(ViewState::modified),
                    unavailable.containsKey(file.filename()) && Files.isRegularFile(ConfigLocations.file(FMLPaths.CONFIGDIR.get(),file.filename())) || controls.documentIssue(file.id()) || states.stream().anyMatch(ViewState::invalid),
                    states.stream().anyMatch(ViewState::restart)));
        }
    }
    private boolean matches(Page target, Destination entry) {
        return SettingsSearch.matches(query, category(target.category()).getString(), text("mod." + target.label()).getString(),
                entry == null ? "" : entry.label().getString(), entry == null ? "" : entry.summary().getString(), entry == null ? target.id() : entry.id());
    }
    private static Component fieldName(JsonEdits edits, String key) { return text("field." + edits.definition().help() + "." + key); }
    private void buildPage(Page page) {
        shown = visibleDestinations(page).stream().map(Destination::id).collect(java.util.stream.Collectors.toSet());
        if (shown.isEmpty()) content.row(text("no_results"),text("filter_help"));
        if (serverSettings.selected() && sections(page).isEmpty()) content.row(text("context_local_only"), Component.empty());
        for (var section : sections(page)) {
            if (!shown.contains(section.id())) continue;
            content.beginGroup(section.id(), text(section.kind().label));
            for (var item : items(section)) {
                if (!shown.contains(item.id()) && !(item instanceof SettingsCatalog.File file && shown.stream()
                        .anyMatch(anchor -> anchor.startsWith("field/" + file.entry().id() + "/")))) continue;
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
        if (!saveReport.isEmpty()) {
            content.section("save-receipts", text(serverSettings.selected() ? "policy_state" : "save_results"));
            for (var line : saveReport) content.row(Component.empty(), line);
        }
        content.row(text(serverSettings.selected() ? "server_status_intro" : "status_intro"), Component.empty());
        content.row(text("reload"), text("reload_summary"), button(text("reload"), 95, this::reload));
        folder();
        for (var file : SettingsCatalog.FILES) {
            if (serverSettings.selected() && !ServerSettingsContext.Document.supports(file.filename())) continue;
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
        Component help = (serverSettings.selected() ? text("server_file_policy") : Component.translatable(file.summaryKey())).copy().append("\n").append(text(serverSettings.selected() ? "server_policy_timing" : "timing." + file.timing().name().toLowerCase(Locale.ROOT)));
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
    private static boolean presentedField(SettingsCatalog.FileEntry file, RegisteredEdits edits, Page page, String key) {
        if (page != null && !file.presents(page, key)) return false;
        if (!file.help().equals("rendering_client")) return true;
        if ((key.equals("clouds.absolute") || key.equals("clouds.offset")) && edits.binding(key).issue() != null) return true;
        String mode = edits.value("clouds.mode");
        return switch (key) {
            case "clouds.absolute" -> mode.equals("ABSOLUTE");
            case "clouds.offset" -> !mode.equals("ABSOLUTE");
            default -> true;
        };
    }
    private Component registeredValue(RegisteredEdits.Field field, String value) {
        if (field.defaultValue() instanceof design.inherent.skyweavecompat.climate.CloudPlacement.Mode)
            return text("rendering.cloud_mode." + value.toLowerCase(Locale.ROOT));
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
            var fileDefinition = SettingsCatalog.file(file);
            if (!presentedField(fileDefinition, edits, page, field.key()) || !shown.contains(fileDefinition.field(field.key()).anchor())) continue;
            var owner = fileDefinition.fieldOwner(field.key());
            boolean admitted = owner == null || OptionalIntegrations.enabled(owner);
            var path = field.binding().getPath();
            String group = String.join(" / ", path.subList(0, path.size() - 1));
            if (!group.isEmpty() && !group.equals(lastGroup)) {
                content.heading(Component.translatableWithFallback("skyweave_compat.settings.registered_group." + group, group));
                lastGroup = group;
            }
            String key = field.key(), id = SettingsCatalog.field(file, key).anchor();
            Component label = registeredName(file, field);
            Component help = registeredHelp(field, fileDefinition, admitted, false);
            if (field.definition().kind() == SettingDefinition.Kind.STRINGS) {
                buildCollection(label, help, edits.binding(key), file, () -> validation(edits));
            } else if (field.choices().isEmpty()) {
                var widgets = controls.field(label, edits.binding(key), () -> validateLater(file, () -> validation(edits)));
                widgets.forEach(widget -> widget.active = admitted);
                content.relatedField(id, relationship(file, key, field.definition()), label, help,
                        widgets.toArray(net.minecraft.client.gui.components.AbstractWidget[]::new));
            } else {
                var widgets = choiceControls(label, edits.binding(key), field.choices(), value -> field.defaultValue() instanceof Boolean
                        || field.defaultValue() instanceof design.inherent.skyweavecompat.climate.CloudPlacement.Mode
                        ? registeredValue(field, value) : choiceLabel(edits.binding(key).id(), field.definition(), value));
                widgets.forEach(widget -> widget.active = admitted);
                widgets.getFirst().setTooltip(Tooltip.create(registeredHelp(field, fileDefinition, admitted, true)));
                content.relatedField(id, relationship(file, key, field.definition()), label, help,
                        widgets.toArray(net.minecraft.client.gui.components.AbstractWidget[]::new));
            }
        }
    }
    private List<net.minecraft.client.gui.components.AbstractWidget> choiceControls(Component label, SettingsBinding binding,
            List<String> choices, java.util.function.Function<String, Component> labels) {
        var current = labels.apply(binding.read().get());
        var choose = valueButton(current, label, Math.clamp(font.width(current) + 16, 65, 160), () -> {
            remember(); contentFocus = binding.id().anchor() + "/0"; focused.put(route(), contentFocus);
            if (choices.size() > 4) {
                var entries = choices.stream().map(key -> new SettingsChoiceScreen.Choice(key, labels.apply(key))).toList();
                minecraft.setScreen(new SettingsChoiceScreen(this, label, entries, binding.read().get(), value -> {
                    binding.definition().parse(value); binding.edit().accept(value);
                }));
            } else {
                int index = choices.indexOf(binding.read().get());
                binding.edit().accept(choices.get((index + 1) % choices.size())); rebuildWidgets();
            }
        });
        var reset = valueButton(text("reset"), label, 42, () -> { remember(); binding.reset(); rebuildWidgets(); });
        reset.setTooltip(Tooltip.create(text("default", binding.definition().defaultText())));
        return List.of(choose, reset);
    }
    private static Component choiceLabel(SettingId id, SettingDefinition definition, String key) {
        if (definition.valueType().isEnum()) for (Object value : definition.valueType().getEnumConstants())
            if (((Enum<?>) value).name().equals(key) && value instanceof net.neoforged.neoforge.common.TranslatableEnum translated)
                return translated.getTranslatedName();
        return Component.translatableWithFallback("skyweave_compat.settings.choice." + id.document() + "." + id.field() + "." + key,
                registeredFallback(key.toLowerCase(Locale.ROOT)));
    }
    private Component registeredHelp(RegisteredEdits.Field field, SettingsCatalog.FileEntry file, boolean admitted, boolean full) {
        String description = field.spec().getComment();
        var help = Component.literal(description == null ? "" : full ? description : description.lines().findFirst().orElse(""))
                .append("\n").append(text("after_save." + field.timing(file.filename()).name().toLowerCase(Locale.ROOT)));
        for (String notice : file.notices(field.key())) help.append("\n").append(text(notice));
        if (!admitted) help.append("\n").append(text("rendering.hook_unavailable"));
        if (field.spec().getRange() != null) help.append("\n").append(text("bounds", field.spec().getRange().toString()));
        return help;
    }
    private Component validation(RegisteredEdits edits) {
        var issues = edits.issues(); controls.feedback(SettingsCatalog.file(edits.filename()).id(), issues);
        if (!issues.isEmpty()) return text("invalid").append(" ").append(issues.getFirst().message()).withStyle(net.minecraft.ChatFormatting.RED);
        try { return edits.nativeMatchesFile() ? Component.empty() : text("native_reload_pending").withStyle(net.minecraft.ChatFormatting.YELLOW); }
        catch (Exception failure) { return failure("invalid", failure).withStyle(net.minecraft.ChatFormatting.RED); }
    }
    private void buildJson(JsonEdits edits) {
        String file = edits.definition().filename();
        content.row("validation/" + file, Component.empty(), validation(edits));
        String group = "";
        for (String key : edits.keys()) {
            if (!shown.contains(SettingsCatalog.field(file,key).anchor())) continue;
            String nextGroup = key.contains(".") ? key.substring(0, key.indexOf('.')) : "";
            String groupAnchor = jsonGroup(edits,key);
            boolean folded = groupAnchor != null && !filtering() && collapsed.contains(groupAnchor);
            if (!nextGroup.equals(group) && !nextGroup.isEmpty()) {
                Component groupLabel = text("field." + edits.definition().help() + "." + nextGroup);
                var toggle = valueButton(Component.literal(folded ? "+" : "−"), groupLabel, 20, () -> {
                    remember();
                    if (!collapsed.remove(groupAnchor)) collapsed.add(groupAnchor);
                    contentFocus=groupAnchor+"/0"; focused.put(route(),contentFocus); rebuildWidgets();
                });
                toggle.setTooltip(Tooltip.create(text(folded ? "expand_group" : "collapse_group")));
                toggle.active = !filtering();
                content.anchorRow(groupAnchor,groupLabel,Component.empty(),toggle);
            }
            group = nextGroup;
            if (folded) continue;
            String id = SettingsCatalog.field(file, key).anchor();
            for (var range : SettingsCatalog.ranges(file)) if (key.equals(range.minimum()))
                content.heading(Component.translatable(range.labelKey()));
            Component label = fieldName(edits, key);
            Component help = text("field." + edits.definition().help() + "." + key + ".help");
            var observed = serverSettings.observed(file,key);
            if (serverSettings.selected() && observed!=null) help = help.copy().append("\n").append(text("effective_value",observed));
            switch (edits.kind(key)) {
                case NUMBER, STRING -> {
                    if (edits.field(key) != null && edits.field(key).kind() == SettingDefinition.Kind.CHOICE) {
                        var binding = edits.binding(key);
                        content.relatedField(id, relationship(file, key, edits.field(key)), label, help,
                                choiceControls(label, binding, edits.field(key).choices(), value -> choiceLabel(binding.id(), edits.field(key), value))
                                        .toArray(net.minecraft.client.gui.components.AbstractWidget[]::new));
                        break;
                    }
                    if (edits.field(key) != null) {
                        var description=help;
                        content.relatedField(id, relationship(file, key, edits.field(key)), label, fieldHelp(edits,key,description), controls.field(label, edits.binding(key),
                                () -> {
                                    content.summary(id,fieldHelp(edits,key,description));
                                    validateLater(file, () -> validation(edits));
                                })
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
                case STRINGS -> buildCollection(label, help, edits.binding(key), file, () -> validation(edits));
            }
        }
    }
    private void buildCollection(Component label, Component help, SettingsBinding binding, String file,
            java.util.function.Supplier<Component> validation) {
        String anchor = binding.id().anchor();
        content.anchorRow(anchor, label, help);
        var collection = controls.collection(label, binding, content.fieldWidth(), focus -> {
            remember(); rebuildWidgets();
            requestedFocus = new FocusRequest(content, focus);
        }, () -> validateLater(file, validation));
        for (var row : collection.rows()) content.field(row.id(), Component.empty(), Component.empty(),
                row.widgets().toArray(net.minecraft.client.gui.components.AbstractWidget[]::new));
        content.row(anchor + "/actions", Component.empty(), Component.empty(),
                collection.actions().toArray(net.minecraft.client.gui.components.AbstractWidget[]::new));
    }
    private Component validation(JsonEdits edits) {
        var issues = edits.issues(); controls.feedback(SettingsCatalog.file(edits.definition().filename()).id(), issues);
        return issues.isEmpty() ? Component.empty() : text("invalid").append(" ").append(issues.getFirst().message()).withStyle(net.minecraft.ChatFormatting.RED);
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
    private static Component fieldHelp(JsonEdits edits, String key, Component description) {
        var definition=edits.field(key); var help=withUnits(description,definition);
        var input=definition.inspect(edits.value(key));
        String hint=input.state()==SettingDefinition.InputState.VALID
                ? SettingsCatalog.file(edits.definition().filename()).valueHint(key,input.value()) : null;
        return hint==null ? help : help.copy().append("\n").append(text(hint));
    }
    private static Component withUnits(Component help, SettingDefinition definition) {
        if (definition.unit().isEmpty()) return help;
        return help.copy().append("\n").append(text("unit." + definition.unit()));
    }
    private static net.minecraft.network.chat.MutableComponent failure(String key, Exception failure) {
        return text(key).append(" ").append(Component.literal(String.valueOf(failure.getMessage())));
    }
    private void folder() { content.row(Component.empty(), Component.empty(), button(text("open_folder"), 115, () -> Util.getPlatform().openFile(ConfigLocations.ownedDirectory(FMLPaths.CONFIGDIR.get()).toFile()))); }

    private Component contextIntro() { return text(serverSettings.selected() ? "server_context_intro" : "local"); }
    private boolean canSave() { return dirty() && !serverSettings.busy() && (!serverSettings.selected() || serverSettings.ready()); }
    private void revertDraft() {
        if (serverSettings.busy()) return;
        remember();
        if (!serverSettings.selected()) {
            if (draft != null) draft.discard();
            toml.values().forEach(RegisteredEdits::discard);
        }
        json.values().stream().filter(edits -> !serverSettings.selected()
                || ServerSettingsContext.Document.supports(edits.definition().filename())).forEach(JsonEdits::discard);
        validationJobs.clear(); controls.clearFeedback(); feedback = text("reverted"); rebuildWidgets();
    }
    private List<SettingsReviewScreen.Change> reviewChanges() {
        var changes = new ArrayList<SettingsReviewScreen.Change>();
        if (!serverSettings.selected() && draft != null) for (Feature feature : Feature.values()) if (draft.edited(feature))
            changes.add(new SettingsReviewScreen.Change(new SettingId("skyweave_compat:features", feature.key), FeatureSettings.FILE,
                    featureName(feature), on(draft.saved().enabled(feature)).getString(), on(draft.value(feature)).getString(),
                    text("after_save.game_restart"), state(feature)));
        for (var file : SettingsCatalog.FILES) {
            if (serverSettings.selected() && !ServerSettingsContext.Document.supports(file.filename())) continue;
            var owned = json.get(file.filename()); var nativeEdits = toml.get(file.filename());
            if (owned != null) for (String key : owned.keys()) if (owned.edited(key))
                changes.add(new SettingsReviewScreen.Change(file.field(key), file.filename(), fieldName(owned,key),
                        owned.savedValue(key), owned.value(key), text("after_save." + file.timing(key).name().toLowerCase(Locale.ROOT)),
                        observed(file.filename(),key)));
            if (nativeEdits != null) for (var field : nativeEdits.fields()) if (nativeEdits.changedKeys().contains(field.key()))
                changes.add(new SettingsReviewScreen.Change(file.field(field.key()), file.filename(), registeredName(file.filename(),field),
                        nativeEdits.savedValue(field.key()), nativeEdits.value(field.key()), registeredHelp(field,file,
                                file.fieldOwner(field.key()) == null || OptionalIntegrations.enabled(file.fieldOwner(field.key())),true),
                        text("effective_unknown")));
        }
        return List.copyOf(changes);
    }
    private Component observed(String file, String key) {
        Component value = serverSettings.selected() ? serverSettings.observed(file,key) : null;
        return value == null ? text("effective_unknown") : text("effective_value",value);
    }
    private void reviewDraft() {
        if (!dirty() || serverSettings.busy()) return;
        remember(); var snapshot = reviewChanges();
        minecraft.setScreen(new SettingsReviewScreen(this, snapshot, contextIntro(), id -> {
            minecraft.setScreen(this);
            if (id.document().equals("skyweave_compat:features")) {
                for (Feature feature : Feature.values()) if (feature.key.equals(id.field())) {
                    navigate(SettingsCatalog.page(SettingsCatalog.group(feature)),"feature/"+feature.key); break;
                }
            } else reveal(SettingsCatalog.document(id.document()),id.field());
        }, () -> {
            minecraft.setScreen(this);
            if (!snapshot.equals(reviewChanges())) { feedback = text("review_changed"); rebuildWidgets(); return; }
            saveDraft();
        }));
    }
    private void saveDraft() {
        if (!canSave() || !validateDraft()) return;
        if (serverSettings.selected()) { writeServerDraft(); return; }
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
    /** Validate the owning candidates before persistence and route only explicitly attributed errors. */
    private boolean validateDraft() {
        for (var file : SettingsCatalog.FILES) {
            if (serverSettings.selected() && !ServerSettingsContext.Document.supports(file.filename())) continue;
            var owned = json.get(file.filename());
            var nativeEdits = toml.get(file.filename());
            List<SettingIssue> issues = owned != null ? owned.issues() : nativeEdits != null ? nativeEdits.issues() : List.of();
            controls.feedback(file.id(), issues);
            if (!issues.isEmpty()) {
                var issue = issues.getFirst();
                feedback = text("invalid").append(" ").append(issue.message());
                reveal(file, issue.field()); return false;
            }
        }
        return true;
    }
    private void reveal(SettingsCatalog.FileEntry file, String field) {
        // Validation must remain reachable even when a future filter hides the field.
        view = View.ALL; query = ""; beforeFilter = null; restoredFilterPosition=false; searchPending = resultSetChanged = false;
        if (search != null) search.setValue("");
        var target = SettingsCatalog.page(file.group());
        if (file.help().equals("rendering_client") && field.startsWith("worldText.")) target = SettingsCatalog.page(Group.IRIS);
        var id = field.isEmpty() ? null : file.field(field);
        String anchor = id == null ? "file/" + file.id() : id.anchor();
        if (file.help().equals("journal")) anchor = switch(field) {
            case "itemImages" -> "preference/images"; case "conditionSymbols" -> "preference/symbols";
            case "notifications" -> "preference/notifications"; default -> anchor;
        };
        navigate(target,anchor);
        String focus = id == null ? null : content.fieldFocus(id.anchor(), controls.input(id));
        if (focus != null) requestedFocus = new FocusRequest(content, focus);
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
            var report = new ArrayList<Component>();
            for (var receipt : result.receipts()) {
                if (receipt.state() == SettingsCommit.Persistence.WRITTEN
                        && receipt.filename().equals(design.inherent.skyweavecompat.maps.MapZoomSettings.FILE)
                        && OptionalIntegrations.enabled(Feature.HOOFPRINT_ZOOM))
                    design.inherent.skyweavecompat.maps.MapZoomSettings.acceptSaved(receipt.savedText());
                var line = Component.literal(receipt.filename()+": ").append(text("persistence."+receipt.state().name().toLowerCase(Locale.ROOT)));
                if (receipt.failure()!=null) line.append(". ").append(boundedMessage(receipt.failure()));
                report.add(line);
            }
            for (var edits : toml.values()) if (edits.warning()!=null) report.add(Component.literal(edits.warning()));
            saveReport = List.copyOf(report);
            feedback = text(result.success() ? "save_report_success" : "save_report_partial");
        } catch (Exception failure) { feedback = failure("save_failed", failure); }
        rebuildWidgets();
    }
    private static Component boundedMessage(Exception failure) {
        String value = String.valueOf(failure.getMessage());
        return Component.literal(value.length()<=512 ? value : value.substring(0,512));
    }
    private void writeServerDraft() {
        remember();
        try {
            var writes = new ArrayList<SettingsCommit.Write>();
            for (var edits : json.values()) if (ServerSettingsContext.Document.supports(edits.definition().filename()) && edits.dirty())
                writes.add(edits.prepare(minecraft::execute));
            serverSettings.submit(List.copyOf(writes));
        } catch (Exception failure) { feedback = failure("save_failed",failure); rebuildWidgets(); }
    }
    private void serverChanged() {
        feedback = serverSettings.message();
        var report = new ArrayList<Component>();
        for (var receipt : serverSettings.receipts()) {
            var line = Component.literal(SettingsCatalog.document(receipt.document()).filename()+": ")
                    .append(text("persistence."+receipt.persistence().name().toLowerCase(Locale.ROOT)))
                    .append("; ").append(text("application."+receipt.application().name().toLowerCase(Locale.ROOT)));
            if (receipt.failure()!=null) line.append(". ").append(boundedMessage(receipt.failure()));
            report.add(line);
        }
        saveReport = List.copyOf(report);
        if (minecraft!=null && minecraft.screen==this) { remember(); rebuildWidgets(); }
    }
    private void switchContext() {
        if (serverSettings.busy()) return;
        boolean next = !serverSettings.selected();
        Runnable change = () -> {
            serverSettings.close(); validationJobs.clear(); load(); loadJson(); loadRegistered(); controls=null; saveReport=List.of();
            // A new authority context gets fresh navigation state, not another context's filtered bookmark.
            view=View.ALL; query=""; beforeFilter=null; restoredFilterPosition=false; searchPending=resultSetChanged=false;
            if (search!=null) search.setValue("");
            positions.clear(); scroll.clear(); focused.clear(); contentFocus=navigationFocus=null; navigationScroll=0;
            detail=null;
            if (next) { page=SettingsCatalog.page(Group.NAVIGATION); selectedEntry=page.id(); expanded.clear(); expanded.add(page.category()); serverSettings.open(); }
            rebuildWidgets();
        };
        if (dirty()) minecraft.setScreen(new ConfirmScreen(answer -> { minecraft.setScreen(this); if (answer) change.run(); },
                text("discard_title"),text("context_discard"),text("discard"),text("keep_editing")));
        else change.run();
    }
    private void leave() { serverSettings.close(); minecraft.setScreen(parent); }
    private void reload() {
        if (serverSettings.busy()) return;
        remember(); Runnable reload = () -> { validationJobs.clear(); load(); loadJson(); loadRegistered(); controls=null;
            if (!serverSettings.selected() && OptionalIntegrations.enabled(Feature.HOOFPRINT_ZOOM))
                design.inherent.skyweavecompat.maps.MapZoomSettings.load();
            if (serverSettings.selected()) serverSettings.open();
            rebuildWidgets(); };
        if (dirty()) minecraft.setScreen(new ConfirmScreen(answer -> { minecraft.setScreen(this); if (answer) reload.run(); }, text("reload_title"), text("reload_message"), text("reload"), text("keep_editing")));
        else reload.run();
    }
    @Override public void tick() {
        serverSettings.tick();
        applyRequestedFocus();
        long now = Util.getMillis();
        var ready = validationJobs.entrySet().stream().filter(entry -> entry.getValue().after <= now).toList();
        for (var entry : ready) {
            validationJobs.remove(entry.getKey());
            content.summary("validation/" + entry.getKey(), entry.getValue().validate.get());
            if (view != View.ALL) searchPending = true;
        }
        if (searchPending) {
            searchPending = false;
            boolean newResults=resultSetChanged; resultSetChanged=false;
            rebuildFiltered(newResults);
        }
        if (language != net.minecraft.locale.Language.getInstance() || resourceRevision != ClientResourceRevision.revision()) {
            remember(); rebuildWidgets();
        }
    }
    @Override protected void setInitialFocus() {
        if (getFocused() == null || !children().contains(getFocused())) super.setInitialFocus();
    }
    @Override public void resize(Minecraft client, int width, int height) { remember(); super.resize(client, width, height); }
    private void applyRequestedFocus() {
        var request = requestedFocus; requestedFocus = null;
        if (request == null || minecraft.screen != this || request.panel() != content) return;
        setDragging(false); content.setDragging(false);
        if (getFocused() != content) setFocused(content);
        content.restoreFocus(request.key());
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        boolean handled = super.keyPressed(key, scan, modifiers);
        applyRequestedFocus(); return handled;
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        var previousView = viewButton;
        boolean handled = super.mouseClicked(x, y, button);
        // Native dispatch assigns focus after the callback, which may have replaced its widgets.
        var focused = getFocused();
        if (minecraft.screen == this && focused != null && !children().contains(focused)) {
            if (focused == previousView) setFocused(viewButton);
            else if (focused instanceof SettingsPanel previous) {
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
        applyRequestedFocus();
        return handled;
    }
    @Override public void onClose() {
        if (detail != null) {
            remember(); detail = null; contentFocus = focused.get(route()); rebuildWidgets(); return;
        }
        remember();
        if (dirty()) minecraft.setScreen(new ConfirmScreen(answer -> { if (answer) { if (draft != null) draft.discard(); json.values().forEach(JsonEdits::discard); toml.values().forEach(RegisteredEdits::discard); leave(); } else minecraft.setScreen(this); }, text("discard_title"), text("discard_message"), text("discard"), text("keep_editing")));
        else leave();
    }
    @Override public void render(GuiGraphics graphics, int mx, int my, float tick) {
        updateSelection();
        renderBackground(graphics, mx, my, tick); save.active = canSave();
        revert.active = review.active = dirty() && !serverSettings.busy(); super.render(graphics, mx, my, tick);
        graphics.drawString(font, title, 12, 10, 0xffffff);
        graphics.drawWordWrap(font, contextIntro(), 12, 24, Math.max(100, width - 24), 0xc0c0c0);
        if (!notice.getString().isEmpty()) graphics.drawWordWrap(font, notice, 12, noticeY + 2, noticeWidth, 0xffffff);
        int available = Math.max(0, width - save.getWidth() - 112);
        if (available > 90) {
            Component hint = text(serverSettings.selected() ? "server_policy_footer" : dirty() ? "save_scope" : "restart");
            int textHeight = font.split(hint, available).size() * font.lineHeight;
            int textY = save.getY() + (save.getHeight() - textHeight) / 2 + 1;
            if (textHeight <= save.getHeight()) graphics.drawWordWrap(font, hint, 100, textY, available, 0xc0c0c0);
        }
    }
}
