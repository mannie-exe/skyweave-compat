package design.inherent.skyweavecompat.clientprobe;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.anti_ad.mc.common.gui.NativeInputContext;
import org.anti_ad.mc.common.input.GlobalInputHandler;
import org.anti_ad.mc.ipnext.config.Hotkeys;

/** Packaged upstream input/action witness in a marked disposable integrated world. */
public final class ClientIpnProbe {
    public static int mouseTweaksWheelCalls, mouseTweaksDragClicks;
    public static int reloads;
    public static int sorts;
    private final Path root = Minecraft.getInstance().gameDirectory.toPath();
    private final JsonObject report = new JsonObject();
    private boolean opened, complete;
    private boolean focusRequested;
    private int ticks;
    private int phase;
    private net.minecraft.client.multiplayer.ClientLevel settledLevel;
    private int joinedSamples;
    private ClientIpnInventoryProbe inventoryProbe;

    ClientIpnProbe() {
        if (!Files.isRegularFile(root.resolve("ipn-review-fixture.json")))
            throw new IllegalStateException("Expected an owned IPN fixture marker");
        report.addProperty("scope", "Actual native integrated client, libIPN dispatcher and IPN reload callback; synthetic event delivery, not OS IME qualification");
        report.addProperty("ipn", ModList.get().getModContainerById("inventoryprofilesnext").orElseThrow().getModInfo().getVersion().toString());
        report.addProperty("libipn", ModList.get().getModContainerById("libipn").orElseThrow().getModInfo().getVersion().toString());
        NeoForge.EVENT_BUS.addListener(this::tick);
    }

    private void tick(ClientTickEvent.Post ignored) {
        var mc = Minecraft.getInstance();
        if (complete || ++ticks % 10 != 0 || mc.getOverlay() != null) return;
        try {
            if (!focusRequested) {
                focusRequested = true;
                org.lwjgl.glfw.GLFW.glfwFocusWindow(mc.getWindow().getWindow());
                return;
            }
            if (!mc.isWindowActive()) return;
            if (phase == 1 && mc.level == null && mc.getSingleplayerServer() == null && mc.screen instanceof TitleScreen) {
                phase = 2;
                mc.createWorldOpenFlows().openWorld("ipn-input-review", () -> mc.setScreen(new TitleScreen()));
                return;
            }
            if (!opened && mc.screen instanceof TitleScreen) {
                opened = true;
                boolean restarting = Boolean.getBoolean("skyweave_compat.probe.ipn.reopen");
                report.addProperty("worldScope", restarting ? "Fresh disposable world after native configuration JVM restart" : "Fresh world followed by same-process saved-world reconnect");
                var rules = new net.minecraft.world.level.GameRules();
                rules.getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(false, null);
                rules.getRule(net.minecraft.world.level.GameRules.RULE_SPAWN_CHUNK_RADIUS).set(0, null);
                mc.createWorldOpenFlows().createFreshLevel(restarting ? "ipn-input-review-restart" : "ipn-input-review", new net.minecraft.world.level.LevelSettings(
                        "IPN input review", net.minecraft.world.level.GameType.CREATIVE, false,
                        net.minecraft.world.Difficulty.PEACEFUL, true, rules, net.minecraft.world.level.WorldDataConfiguration.DEFAULT),
                        new net.minecraft.world.level.levelgen.WorldOptions(42L, false, false),
                        registries -> registries.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET)
                                .get(net.minecraft.resources.ResourceLocation.withDefaultNamespace("flat")).createWorldDimensions(), new TitleScreen());
                return;
            }
            if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null || ticks < 80) return;
            if (settledLevel != mc.level) { settledLevel = mc.level; joinedSamples = 0; }
            if (++joinedSamples < 5) return;
            if (phase == 1) return;
            if (phase == 3) {
                if (!inventoryProbe.tick()) return;
                mc.setScreen(new ChatScreen("")); key(82, 1);
                phase = 1;
                mc.level.disconnect(); mc.disconnect(new TitleScreen()); mc.setScreen(new TitleScreen());
                return;
            }
            if (phase == 2) {
                var reconnect = new JsonObject(); int before = reloads;
                mc.setScreen(null);
                key(89, 1); key(89, 0); key(82, 0);
                reconnect.addProperty("carriedReloads", reloads - before);
                reconnect.addProperty("freshReloads", chord(java.util.List.of(82, 89)));
                reconnect.addProperty("pressedKeysEmpty", GlobalInputHandler.INSTANCE.getPressedKeys().isEmpty());
                report.add("reconnect", reconnect);
                Hotkeys.INSTANCE.getRELOAD_CUSTOM_CONFIGS().getMainKeybind().setKeyCodes(java.util.List.of(296, 297));
                org.anti_ad.mc.ipnext.config.ConfigScreenSettings.INSTANCE.getSaveManager().save();
                report.addProperty("persistedRebinding", true);
                report.addProperty("status", "OBSERVED"); finish(); return;
            }
            var binding = Hotkeys.INSTANCE.getRELOAD_CUSTOM_CONFIGS().getMainKeybind().getKeyCodes();
            report.add("reloadBinding", new GsonBuilder().create().toJsonTree(binding));
            report.addProperty("windowActiveAtAction", mc.isWindowActive());
            report.addProperty("reloadSettings", Hotkeys.INSTANCE.getRELOAD_CUSTOM_CONFIGS().getMainKeybind().getSettings().toString());
            boolean reopening = Boolean.getBoolean("skyweave_compat.probe.ipn.reopen");
            if (!binding.equals(reopening ? java.util.List.of(296, 297) : java.util.List.of(82, 89)))
                throw new IllegalStateException("Expected the actual default or saved native binding");
            mc.setScreen(null);
            report.addProperty("outsideTextReloads", chord(binding));
            var chat = new ChatScreen("");
            mc.setScreen(chat);
            int before = reloads;
            for (int key : binding) {
                key(key, 1);
                if (!reopening) chat.charTyped((char) Character.toLowerCase(key), 0);
            }
            for (int key : binding.reversed()) key(key, 0);
            report.addProperty("chatReloads", reloads - before);
            report.addProperty("chatScreenRetained", mc.screen == chat);
            report.addProperty("chatText", ((net.minecraft.client.gui.components.EditBox) chat.children().stream()
                    .filter(net.minecraft.client.gui.components.EditBox.class::isInstance).findFirst().orElseThrow()).getValue());
            report.addProperty("pressedKeysEmpty", GlobalInputHandler.INSTANCE.getPressedKeys().isEmpty());
            if (Boolean.getBoolean("skyweave_compat.probe.ipn.extended") && !reopening) {
                var expanded = new JsonObject(); report.add("extended", expanded); extended(expanded);
                inventoryProbe = new ClientIpnInventoryProbe(report); phase = 3;
                return;
            }
            report.addProperty("status", "OBSERVED");
            finish();
        } catch (Throwable failure) {
            report.addProperty("status", "FAIL");
            report.addProperty("error", failure.toString());
            failure.printStackTrace();
            finish();
        }
    }

    private static int chord(java.util.List<Integer> keys) {
        int before = reloads;
        for (int key : keys) key(key, 1);
        for (int key : keys.reversed()) key(key, 0);
        return reloads - before;
    }

    private static JsonObject extended(JsonObject result) {
        var mc = Minecraft.getInstance();
        var binding = Hotkeys.INSTANCE.getRELOAD_CUSTOM_CONFIGS().getMainKeybind();
        var keys = java.util.List.copyOf(binding.getKeyCodes());
        var settings = binding.getSettings();
        try {
            mc.setScreen(new TextScreen(true, true));
            result.addProperty("nestedFocusedReloads", chord(keys));
            mc.setScreen(new TextScreen(false, true));
            result.addProperty("nestedUnfocusedReloads", chord(keys));
            mc.setScreen(new TextScreen(true, false));
            result.addProperty("directFocusedReloads", chord(keys));
            mc.setScreen(new ChatScreen(""));
            int before = reloads;
            key(82, 1);
            mc.setScreen(null);
            key(89, 1); key(89, 0); key(82, 0);
            result.addProperty("typedPrefixThenCloseReloads", reloads - before);
            result.addProperty("freshAfterCloseReloads", chord(keys));

            var release = settings.copy(settings.getContext(), org.anti_ad.mc.common.input.KeybindSettings.KeyAction.RELEASE,
                    settings.getAllowExtraKeys(), settings.getOrderSensitive(), settings.getModifierKey());
            binding.setSettings(release);
            for (boolean reverse : new boolean[]{false, true}) {
                mc.setScreen(new ChatScreen("")); before = reloads;
                key(82, 1); key(89, 1);
                mc.setScreen(null);
                key(reverse ? 89 : 82, 0); key(reverse ? 82 : 89, 0);
                result.addProperty(reverse ? "primaryReleasedFirstReloads" : "prefixReleasedFirstReloads", reloads - before);
            }
            result.addProperty("freshReleaseBindingReloads", chord(keys));
            binding.setSettings(settings);

            mc.setScreen(new ChatScreen(""));
            key(82, 1); key(89, 1);
            var foreign = new org.anti_ad.mc.common.input.MainKeybind("R,Y", settings);
            result.addProperty("unrelatedLibraryBindingActivated", foreign.isActivated());
            key(89, 0); key(82, 0);

            var toggles = org.anti_ad.mc.ipnext.config.ConfigScreenSettings.INSTANCE.getAllToggleSettings();
            var toggle = toggles.stream().min(java.util.Comparator.comparing(org.anti_ad.mc.common.config.options.ConfigKeyToggleBoolean::getKey)).orElseThrow();
            var toggleBinding = toggle.getMainKeybind();
            var toggleKeys = java.util.List.copyOf(toggleBinding.getKeyCodes());
            var toggleSettings = toggleBinding.getSettings();
            boolean toggleValue = toggle.getBooleanValue();
            try {
                toggleBinding.setKeyCodes(java.util.List.of(298)); // F9 in this owned fixture only.
                toggleBinding.setSettings(settings);
                mc.setScreen(null);
                chord(java.util.List.of(298));
                result.addProperty("configurationToggleKey", toggle.getKey());
                result.addProperty("configurationListenerOutsideToggled", toggle.getBooleanValue() != toggleValue);
                boolean prior = toggle.getBooleanValue();
                mc.setScreen(new ChatScreen(""));
                chord(java.util.List.of(298));
                result.addProperty("configurationListenerTextToggled", toggle.getBooleanValue() != prior);
            } finally {
                toggleBinding.setKeyCodes(toggleKeys); toggleBinding.setSettings(toggleSettings); toggle.setValue(toggleValue);
            }

            mc.setScreen(new ChatScreen(""));
            GlobalInputHandler.INSTANCE.setCurrentAssigningKeybind(binding);
            key(296, 1); key(297, 1); key(297, 0); key(296, 0);
            result.add("nativeCapturedKeys", new GsonBuilder().create().toJsonTree(binding.getKeyCodes()));
            result.addProperty("nativeCaptureFinished", GlobalInputHandler.INSTANCE.getCurrentAssigningKeybind() == null);
            GlobalInputHandler.INSTANCE.setCurrentAssigningKeybind(binding);
            key(340, 1); mouseChord(); key(340, 0);
            result.add("nativeCapturedModifierMouse", new GsonBuilder().create().toJsonTree(binding.getKeyCodes()));
            result.addProperty("nativeModifierMouseCaptureFinished", GlobalInputHandler.INSTANCE.getCurrentAssigningKeybind() == null);
            GlobalInputHandler.INSTANCE.setCurrentAssigningKeybind(binding);
            key(256, 1); key(256, 0);
            result.addProperty("nativeEscapeUnbound", binding.getKeyCodes().isEmpty());
            binding.setKeyCodes(keys);
            binding.setSettings(release);
            mc.setScreen(null); key(82, 1); key(89, 1);
            before = reloads;
            mc.setWindowActive(false); mc.setWindowActive(true);
            key(89, 0); key(82, 0);
            result.addProperty("focusLossReleaseReloads", reloads - before);
            result.addProperty("pressedKeysEmpty", GlobalInputHandler.INSTANCE.getPressedKeys().isEmpty());
            binding.setSettings(settings);
            var editors = new JsonObject(); result.add("editors", editors); editors(keys, editors);
        } finally {
            GlobalInputHandler.INSTANCE.setCurrentAssigningKeybind(null);
            for (int held : java.util.List.copyOf(GlobalInputHandler.INSTANCE.getPressedKeys())) key(held, 0);
            binding.setKeyCodes(keys); binding.setSettings(settings);
        }
        return result;
    }

    /** Real native editor widgets; reflection is confined to this disposable fixture. */
    private static JsonObject editors(java.util.List<Integer> keys, JsonObject result) {
        var mc = Minecraft.getInstance();
        var mode = mc.gameMode.getPlayerMode();
        try {
            mc.gameMode.setLocalMode(net.minecraft.world.level.GameType.SURVIVAL);
            var inventory = new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player);
            mc.setScreen(inventory);
            var recipes = inventory.getRecipeBookComponent();
            if (!recipes.isVisible()) recipes.toggleVisibility();
            var searchField = recipes.getClass().getDeclaredField("searchBox");
            searchField.setAccessible(true);
            var search = (net.minecraft.client.gui.components.EditBox) searchField.get(recipes);
            if (search == null && !ModList.get().isLoaded("emi")) throw new IllegalStateException("Native recipe search was not created");
            result.addProperty("recipeBookReplaced", search == null);
            if (search != null) {
            search.setFocused(true);
            result.addProperty("recipeFocusedReloads", chord(keys));
            search.setFocused(false);
            result.addProperty("recipeUnfocusedReloads", chord(keys));

            // Render native slot geometry, then retain recipe keyboard focus while moving
            // the pointer. This tests current pointer ownership, including stale hover data.
            search.setFocused(true);
            var slot = inventory.getMenu().getSlot(9);
            int x = inventory.getGuiLeft() + slot.x + 8, y = inventory.getGuiTop() + slot.y + 8;
            move(x, y);
            var graphics = new net.minecraft.client.gui.GuiGraphics(mc, mc.renderBuffers().bufferSource());
            inventory.render(graphics, x, y, 0); graphics.flush();
            result.addProperty("slotRendered", inventory.getSlotUnderMouse() == slot);
            var binding = Hotkeys.INSTANCE.getRELOAD_CUSTOM_CONFIGS().getMainKeybind();
            var old = java.util.List.copyOf(binding.getKeyCodes());
            try {
                binding.setKeyCodes(java.util.List.of(-98));
                result.addProperty("slotMouseReloads", mouseChord());
                move(search.getX() + 2, search.getY() + 2);
                result.addProperty("staleSlotTextMouseReloads", mouseChord());
            } finally { binding.setKeyCodes(old); }
            move(x, y);
            inventory.render(graphics, x, y, 0); graphics.flush();
            int before = sorts;
            var sortBinding = Hotkeys.INSTANCE.getSORT_INVENTORY().getMainKeybind();
            var originalSortKeys = java.util.List.copyOf(sortBinding.getKeyCodes());
            result.add("originalSortBinding", new GsonBuilder().create().toJsonTree(originalSortKeys));
            try { sortBinding.setKeyCodes(java.util.List.of(-98)); mouseChord(); }
            finally { sortBinding.setKeyCodes(originalSortKeys); }
            result.addProperty("nativeMiddleSortsWithRecipeFocus", sorts - before);
            }

            var anvilMenu = new net.minecraft.world.inventory.AnvilMenu(0, mc.player.getInventory());
            anvilMenu.getSlot(0).set(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD));
            var anvil = new net.minecraft.client.gui.screens.inventory.AnvilScreen(anvilMenu, mc.player.getInventory(),
                    net.minecraft.network.chat.Component.literal("Owned rename fixture"));
            mc.setScreen(anvil);
            result.addProperty("anvilFocusedReloads", chord(keys));
            result.addProperty("anvilFocusedNativeEditBox", anvil.getFocused() instanceof net.minecraft.client.gui.components.EditBox);

            var ipnConfig = new org.anti_ad.mc.common.gui.screen.ConfigScreenBase(org.anti_ad.mc.ipnext.config.ConfigScreenSettings.INSTANCE);
            mc.setScreen(ipnConfig);
            ipnConfig.getSearchBox().getTextField().setVanillaFocused(true);
            result.addProperty("ipnConfigSearchReloads", chord(keys));
            ipnConfig.getSearchBox().getTextField().setVanillaFocused(false);

            mc.setScreen(ClientConfigurationProbe.openScreen(null));
            var local = textButtons(mc.screen).stream().filter(button -> button.getMessage().getString().equals("Local files"))
                    .findFirst().orElseThrow(() -> new IllegalStateException("Actual Configure context choice is missing"));
            mc.screen.mouseClicked(local.getX() + 4, local.getY() + 4, 0);
            var workspace = mc.screen;
            var nativeSearch = textFields(workspace, 0).stream().filter(field -> field.getMessage().getString().equals("Search settings"))
                    .findFirst().orElseThrow(() -> new IllegalStateException("Actual Configure search is missing"));
            if (!workspace.mouseClicked(nativeSearch.getX() + 4, nativeSearch.getY() + 4, 0))
                throw new IllegalStateException("Actual Configure search click was refused");
            workspace.mouseReleased(nativeSearch.getX() + 4, nativeSearch.getY() + 4, 0);
            nativeSearch.setValue("");
            int searchBefore = reloads;
            var accepted = new com.google.gson.JsonArray();
            for (int key : keys) { key(key, 1); accepted.add(workspace.charTyped((char)Character.toLowerCase(key), 0)); }
            for (int key : keys.reversed()) key(key, 0);
            result.addProperty("reformSearchReloads", reloads - searchBefore);
            result.add("reformTypedAccepted", accepted);
            result.addProperty("reformSearchText", textFields(workspace, 0).stream()
                    .filter(field -> field.getMessage().getString().equals("Search settings")).findFirst().orElseThrow().getValue());
            result.addProperty("reformScreenRetained", mc.screen == workspace);

            if (ModList.get().isLoaded("emi")) {
                var emi = Class.forName("dev.emi.emi.api.EmiApi");
                mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
                var overlay = (net.minecraft.client.gui.components.EditBox) Class.forName("dev.emi.emi.screen.EmiScreenManager").getField("search").get(null);
                overlay.setFocused(true);
                result.addProperty("emiSearchFocused", (boolean)emi.getMethod("isSearchFocused").invoke(null));
                result.addProperty("emiFocusedReloads", chord(keys));
                overlay.setFocused(false);
            }
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Native editor fixture changed", failure); }
        finally { mc.setScreen(null); mc.gameMode.setLocalMode(mode); }
        return result;
    }

    private static java.util.List<net.minecraft.client.gui.components.Button> textButtons(
            net.minecraft.client.gui.components.events.GuiEventListener node) {
        var buttons = new java.util.ArrayList<net.minecraft.client.gui.components.Button>();
        if (node instanceof net.minecraft.client.gui.components.Button button) buttons.add(button);
        if (node instanceof net.minecraft.client.gui.components.events.ContainerEventHandler group)
            for (var child : group.children()) buttons.addAll(textButtons(child));
        return buttons;
    }

    private static java.util.List<net.minecraft.client.gui.components.EditBox> textFields(
            net.minecraft.client.gui.components.events.GuiEventListener node, int depth) {
        if (depth > 16) throw new IllegalStateException("Fixture widget hierarchy exceeds its search budget");
        var fields = new java.util.ArrayList<net.minecraft.client.gui.components.EditBox>();
        if (node instanceof net.minecraft.client.gui.components.EditBox field) fields.add(field);
        if (node instanceof net.minecraft.client.gui.components.events.ContainerEventHandler group)
            for (var child : group.children()) fields.addAll(textFields(child, depth + 1));
        return fields;
    }

    static void move(double x, double y) throws ReflectiveOperationException {
        var mc = Minecraft.getInstance();
        var method = net.minecraft.client.MouseHandler.class.getDeclaredMethod("onMove", long.class, double.class, double.class);
        method.setAccessible(true);
        method.invoke(mc.mouseHandler, mc.getWindow().getWindow(),
                x * mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth(),
                y * mc.getWindow().getScreenHeight() / mc.getWindow().getGuiScaledHeight());
    }

    static int mouseChord() {
        int before = reloads;
        for (int action : new int[]{1, 0}) GlobalInputHandler.INSTANCE.onMouseButton(
                new NativeInputContext<>(Minecraft.getInstance().screen, false), 2, action, 0);
        return reloads - before;
    }

    private static final class TextScreen extends net.minecraft.client.gui.screens.Screen {
        private final boolean focused, nested;
        TextScreen(boolean focused, boolean nested) {
            super(net.minecraft.network.chat.Component.literal("Owned IPN text fixture"));
            this.focused = focused; this.nested = nested;
        }
        @Override protected void init() {
            var field = new net.minecraft.client.gui.components.EditBox(font, 20, 30, 160, 20,
                    net.minecraft.network.chat.Component.literal("Fixture text"));
            if (nested) {
                var group = addRenderableWidget(new TextGroup(field));
                if (focused) { group.setFocused(field); setFocused(group); field.setFocused(true); }
            } else {
                addRenderableWidget(field);
                if (focused) { setFocused(field); field.setFocused(true); }
            }
        }
    }

    private static final class TextGroup extends net.minecraft.client.gui.components.events.AbstractContainerEventHandler
            implements net.minecraft.client.gui.components.Renderable, net.minecraft.client.gui.narration.NarratableEntry {
        private final net.minecraft.client.gui.components.EditBox field;
        TextGroup(net.minecraft.client.gui.components.EditBox field) { this.field = field; }
        public java.util.List<? extends net.minecraft.client.gui.components.events.GuiEventListener> children() { return java.util.List.of(field); }
        public void render(net.minecraft.client.gui.GuiGraphics graphics, int x, int y, float tick) { field.render(graphics, x, y, tick); }
        public NarrationPriority narrationPriority() { return field.narrationPriority(); }
        public void updateNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) { field.updateNarration(output); }
    }

    static void key(int key, int action) {
        // Native public dispatcher performs its real pressed/previous-key update and invokes
        // registered cancellable, regular, and configuration listeners. Hardware polling is off
        // for synthetic events; the physical event adapter and text composition are separate.
        GlobalInputHandler.INSTANCE.onKey(new NativeInputContext<>(Minecraft.getInstance().screen, false),
                key, 0, action, 0, false, Minecraft.getInstance().getWindow().getWindow());
    }

    private void finish() {
        complete = true;
        try { Files.writeString(root.resolve("ipn-review-result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report)); }
        catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
        Minecraft.getInstance().stop();
    }
}
