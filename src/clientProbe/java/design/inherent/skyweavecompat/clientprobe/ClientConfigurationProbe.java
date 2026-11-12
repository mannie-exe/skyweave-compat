package design.inherent.skyweavecompat.clientprobe;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import design.inherent.skyweavecompat.Feature;
import design.inherent.skyweavecompat.OptionalIntegrations;
import design.inherent.skyweavecompat.client.ConfigurationOverview;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Finite file-controlled native UI checks in an explicitly marked disposable client. */
final class ClientConfigurationProbe {
    private final Path root = FMLPaths.GAMEDIR.get();
    private boolean opened;
    private int ticks, sequence = -1;
    private String lastError;
    private Boolean keyAccepted;
    private JsonObject nativeKey;
    private boolean wayfinderObserver;
    ClientConfigurationProbe() {
        if (!Files.isRegularFile(root.resolve("config-review-fixture.json"))) throw new IllegalStateException("Configuration probe requires its disposable-fixture marker");
        NeoForge.EVENT_BUS.addListener(this::tick);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, true,
                net.neoforged.neoforge.client.event.ScreenEvent.KeyPressed.Pre.class, event -> {
                    nativeKey = new JsonObject(); nativeKey.addProperty("key", event.getKeyCode());
                    nativeKey.addProperty("modifiers", event.getModifiers()); nativeKey.addProperty("canceled", event.isCanceled());
                    nativeKey.addProperty("controlDown", net.minecraft.client.gui.screens.Screen.hasControlDown());
                });
        if (Boolean.getBoolean("skyweave_compat.probe.configuration.journal")) new ClientJournalProbe();
    }
    private void open() {
        var mc = Minecraft.getInstance();
        mc.setScreen(new ConfigurationOverview(ModList.get().getModContainerById("skyweave_compat").orElseThrow(), mc.screen));
    }
    private void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (++ticks % 10 != 0 || mc.getOverlay() != null) return;
        try {
            if (!opened && !Boolean.getBoolean("skyweave_compat.probe.misc") && !Boolean.getBoolean("skyweave_compat.probe.resources") && !Boolean.getBoolean("skyweave_compat.probe.redstone") && !Boolean.getBoolean("skyweave_compat.probe.configuration.journal") && mc.screen instanceof TitleScreen) { opened = true; open(); }
            Path control = root.resolve("config-review-control.json");
            if (Files.isRegularFile(control) && Files.size(control) < 16384) {
                var command = JsonParser.parseString(Files.readString(control)).getAsJsonObject();
                int seq = command.get("sequence").getAsInt();
                if (seq != sequence) {
                    sequence = seq; lastError = null;
                    switch (command.get("operation").getAsString()) {
                        case "open" -> open();
                        case "wayfinder" -> {
                            if(!wayfinderObserver || mc.player==null
                                    || !Files.isRegularFile(root.resolve("misc-review-fixture.json")))
                                throw new IllegalStateException("Expected a connected marked Wayfinder fixture");
                            WayfinderFixture.apply(mc,command);
                        }
                        case "connect" -> {
                            String address=command.get("address").getAsString();
                            if(!Files.isRegularFile(root.resolve("misc-review-fixture.json"))
                                    || !address.matches("127\\.0\\.0\\.1:[0-9]{1,5}"))
                                throw new IllegalArgumentException("Expected a marked loopback fixture");
                            var target=net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(address);
                            if(target.getPort()<1 || target.getPort()>65535)throw new IllegalArgumentException("Invalid loopback port");
                            if(!wayfinderObserver){WayfinderFixture.register();wayfinderObserver=true;}
                            net.minecraft.client.gui.screens.ConnectScreen.startConnecting(new TitleScreen(),mc,target,
                                    new net.minecraft.client.multiplayer.ServerData("Wayfinder loopback",address,
                                            net.minecraft.client.multiplayer.ServerData.Type.OTHER),false,null);
                        }
                        case "click" -> {
                            var found = widgets(mc.screen).stream().filter(w -> w.getMessage().getString().equals(command.get("label").getAsString())).toList();
                            int index = command.has("index") ? command.get("index").getAsInt() : 0;
                            var widget = found.get(index);
                            boolean accepted = mc.screen.mouseClicked(widget.getX() + widget.getWidth() / 2.0, widget.getY() + widget.getHeight() / 2.0, 0);
                            if (!accepted) throw new IllegalStateException("Native click was refused: " + command.get("label"));
                        }
                        case "field" -> {
                            var fields = widgets(mc.screen).stream().filter(w -> w instanceof net.minecraft.client.gui.components.EditBox).toList();
                            ((net.minecraft.client.gui.components.EditBox) fields.get(command.get("index").getAsInt())).setValue(command.get("value").getAsString());
                        }
                        case "key" -> keyAccepted = mc.screen.keyPressed(command.get("key").getAsInt(), 0, command.has("modifiers") ? command.get("modifiers").getAsInt() : 0);
                        case "scroll" -> mc.screen.mouseScrolled(command.get("x").getAsDouble(), command.get("y").getAsDouble(), 0, command.get("amount").getAsDouble());
                        case "drag" -> {
                            double x = command.get("x").getAsDouble(), y = command.get("y").getAsDouble();
                            double end = command.get("endY").getAsDouble();
                            if (!mc.screen.mouseClicked(x, y, 0)) throw new IllegalStateException("Native drag press refused");
                            mc.screen.mouseDragged(x, end, 0, 0, end - y); mc.screen.mouseReleased(x, end, 0);
                        }
                        case "language" -> {
                            String language = command.get("value").getAsString();
                            mc.options.languageCode = language; mc.getLanguageManager().setSelected(language); mc.reloadResourcePacks();
                        }
                        case "scale" -> { mc.options.guiScale().set(command.get("value").getAsInt()); mc.resizeDisplay(); }
                        case "resize" -> org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), command.get("width").getAsInt(), command.get("height").getAsInt());
                        case "text" -> { for (char character : command.get("value").getAsString().toCharArray()) mc.screen.charTyped(character, 0); }
                        case "snapshot" -> {}
                        case "journal-write-and-toggle" -> journalWriter();
                        case "quit" -> mc.stop();
                        default -> throw new IllegalArgumentException("Unknown configuration fixture action");
                    }
                }
            }
            write(lastError);
        } catch (Exception failure) { lastError = failure.toString(); write(lastError); }
    }
    private void journalWriter() throws Exception {
        if(!OptionalIntegrations.enabled(Feature.QUESTLOG_INTERFACE))throw new IllegalStateException("Journal writer fixture requires its real optional owner");
        var path=design.inherent.skyweavecompat.configuration.ConfigLocations.file(FMLPaths.CONFIGDIR.get(),
                design.inherent.skyweavecompat.questlog.client.JournalPreferences.FILE);
        var original=Files.readString(path);var external=original.replace("conditionSymbols = true","conditionSymbols = false");
        if(external.equals(original))throw new IllegalStateException("Expected fresh native journal defaults");
        Files.writeString(path,external);
        var result=new JsonObject();result.addProperty("externalSymbolChoice",false);
        try {design.inherent.skyweavecompat.questlog.client.JournalPreferences.toggleImages();result.addProperty("toggleAccepted",true);}
        catch(RuntimeException refused){result.addProperty("toggleAccepted",false);result.addProperty("refusal",refused.getMessage());}
        var actual=new com.electronwill.nightconfig.toml.TomlParser().parse(Files.readString(path));
        result.addProperty("savedImages",(Boolean)actual.get("itemImages"));
        result.addProperty("savedSymbols",(Boolean)actual.get("conditionSymbols"));
        result.addProperty("nativeImages",design.inherent.skyweavecompat.questlog.client.JournalPreferences.ITEM_IMAGES.get());
        result.addProperty("nativeSymbols",design.inherent.skyweavecompat.questlog.client.JournalPreferences.CONDITION_SYMBOLS.get());
        result.addProperty("externalChoicePreserved",Boolean.FALSE.equals(actual.get("conditionSymbols")));
        ProbeStateFiles.write(root.resolve("journal-writer-observation.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));
    }
    private static ArrayList<AbstractWidget> widgets(GuiEventListener listener) {
        var found = new ArrayList<AbstractWidget>();
        if (listener instanceof AbstractWidget widget) found.add(widget);
        if (listener instanceof ContainerEventHandler container) for (var child : container.children()) found.addAll(widgets(child));
        return found;
    }
    private void write(String error) {
        try {
            var mc = Minecraft.getInstance(); var report = new JsonObject();
            report.addProperty("pid", ProcessHandle.current().pid()); report.addProperty("sequence", sequence); report.addProperty("screen", mc.screen == null ? "none" : mc.screen.getClass().getName());
            if(wayfinderObserver && mc.player!=null)report.add("wayfinder",WayfinderFixture.state(mc));
            report.addProperty("width", mc.getWindow().getGuiScaledWidth()); report.addProperty("height", mc.getWindow().getGuiScaledHeight()); report.addProperty("scale", mc.options.guiScale().get());
            if (error != null) report.addProperty("error", error);
            report.addProperty("controlDown", net.minecraft.client.gui.screens.Screen.hasControlDown());
            report.addProperty("keyAccepted", keyAccepted);
            if (nativeKey != null) report.add("nativeKey", nativeKey);
            if (mc.screen != null) report.addProperty("focusOwner", String.valueOf(mc.screen.getFocused()));
            if (mc.screen != null) report.addProperty("screenFocusAttached", mc.screen.getFocused() == null || mc.screen.children().contains(mc.screen.getFocused()));
            var rows = new JsonArray();
            if (mc.screen != null) for (var widget : widgets(mc.screen)) {
                var row = new JsonObject();row.addProperty("label", widget.getMessage().getString());row.addProperty("x",widget.getX());row.addProperty("y",widget.getY());row.addProperty("width",widget.getWidth());row.addProperty("height",widget.getHeight());row.addProperty("active",widget.active);row.addProperty("focused",widget.isFocused());row.addProperty("font",widget.getMessage().getStyle().getFont().toString());if(widget instanceof net.minecraft.client.gui.components.EditBox input){row.addProperty("value",input.getValue());row.addProperty("selection",input.getHighlighted());row.addProperty("cursor",input.getCursorPosition());row.addProperty("identity",System.identityHashCode(input));}rows.add(row);
            }
            report.add("widgets", rows);
            if (mc.screen instanceof ConfigurationOverview overview) {
                for (String name : java.util.List.of("page", "query", "feedback")) {
                    var field = ConfigurationOverview.class.getDeclaredField(name); field.setAccessible(true);
                    Object value = field.get(overview);
                    report.addProperty(name, value instanceof net.minecraft.network.chat.Component text ? text.getString() : String.valueOf(value));
                }
                var draftsField = ConfigurationOverview.class.getDeclaredField("json"); draftsField.setAccessible(true);
                var files = new JsonObject();
                for (Object value : ((java.util.Map<?, ?>) draftsField.get(overview)).values()) {
                    var edits = (design.inherent.skyweavecompat.configuration.JsonEdits) value;
                    var entry = new JsonObject(); entry.addProperty("dirty", edits.dirty());
                    for (String key : edits.keys()) entry.addProperty(key, edits.value(key));
                    files.add(edits.definition().filename(), entry);
                }
                report.add("files", files);
                var nativeDrafts = ConfigurationOverview.class.getDeclaredField("toml"); nativeDrafts.setAccessible(true);
                var registered = new JsonObject();
                for (Object value : ((java.util.Map<?, ?>) nativeDrafts.get(overview)).values()) {
                    var edits = (design.inherent.skyweavecompat.configuration.RegisteredEdits) value;
                    var entry = new JsonObject(); entry.addProperty("dirty", edits.dirty());
                    var live = new JsonObject();
                    for (var field : edits.fields()) { entry.addProperty(field.key(), edits.value(field.key())); live.addProperty(field.key(), String.valueOf(field.binding().getRaw())); }
                    entry.add("live", live);
                    registered.add(edits.filename(), entry);
                }
                report.add("registered", registered);
                var linkField = ConfigurationOverview.class.getDeclaredField("links"); linkField.setAccessible(true);
                var navigationKeys = new JsonArray();
                for (Object key : ((java.util.Map<?, ?>) linkField.get(overview)).keySet()) navigationKeys.add(String.valueOf(key));
                report.add("navigation.keys", navigationKeys);
                for (String name : java.util.List.of("content", "navigation")) {
                    var panelField = ConfigurationOverview.class.getDeclaredField(name); panelField.setAccessible(true);
                    Object panel = panelField.get(overview);
                    if (name.equals("content")) {
                        var rowField = panel.getClass().getDeclaredField("rows"); rowField.setAccessible(true);
                        var anchors = new JsonArray(); var groups = new JsonArray();
                        for (Object row : (java.util.List<?>) rowField.get(panel)) {
                            var anchor = row.getClass().getDeclaredMethod("anchor"); anchor.setAccessible(true);
                            var boundary = row.getClass().getDeclaredMethod("boundary"); boundary.setAccessible(true);
                            Object key = anchor.invoke(row);
                            if (key != null) anchors.add(String.valueOf(key));
                            if (((Number) boundary.invoke(row)).intValue() == 1) groups.add(String.valueOf(key));
                        }
                        report.add("content.anchors", anchors); report.add("content.groups", groups);
                    }
                    for (String property : java.util.List.of("scroll", "activeSection", "maximumScroll", "focusKey", "highlighted")) {
                        var method = panel.getClass().getDeclaredMethod(property); method.setAccessible(true);
                        Object value = method.invoke(panel);
                        report.addProperty(name + "." + property, value == null ? null : value.toString());
                    }
                }
                var field = ConfigurationOverview.class.getDeclaredField("draft"); field.setAccessible(true);
                var draft = (design.inherent.skyweavecompat.configuration.StartupEdits) field.get(overview);
                if (draft != null) {
                    report.addProperty("dirty", draft.dirty());
                    var values = new JsonObject();var saved = new JsonObject();
                    for (Feature feature : Feature.values()) { values.addProperty(feature.key, draft.value(feature)); saved.addProperty(feature.key, draft.saved().enabled(feature)); }
                    report.add("draft", values);report.add("saved", saved);
                }
            }
            var active = new JsonArray(); for (Feature feature : OptionalIntegrations.selection().active()) active.add(feature.key); report.add("active", active);
            ProbeStateFiles.write(root.resolve("config-review-state.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
        } catch (Exception failure) { throw new IllegalStateException(failure); }
    }
}
