package design.inherent.skyweavecompat.client.input;

import design.inherent.skyweavecompat.input.TextInputEligibility;
import design.inherent.skyweavecompat.mixin.client.input.RecipeSearchAccess;
import design.inherent.skyweavecompat.mixin.client.input.IpnContainerAccess;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener;
import net.neoforged.fml.loading.LoadingModList;
import org.anti_ad.mc.common.config.builder.ConfigDeclaration;
import org.anti_ad.mc.common.config.options.ConfigHotkey;
import org.anti_ad.mc.common.gui.screen.BaseScreen;
import org.anti_ad.mc.common.gui.screen.ConfigScreenBase;
import org.anti_ad.mc.common.input.AlternativeKeybind;
import org.anti_ad.mc.common.input.GlobalInputHandler;
import org.anti_ad.mc.common.input.IKeybind;
import org.anti_ad.mc.ipnext.config.ConfigScreenSettings;

/** Exact IPN config identity and bounded native focus; no input-event or file ownership. */
public final class IpnTextInput {
    private static final TextInputEligibility POLICY = new TextInputEligibility();
    private static final Set<IKeybind> OWNED = Collections.newSetFromMap(new IdentityHashMap<>());
    private static boolean initialized;
    private static BooleanSupplier overlayFocus = () -> false;

    private IpnTextInput() {}

    public static void register(net.neoforged.bus.api.IEventBus bus) {
        bus.addListener((net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) -> event.enqueueWork(IpnTextInput::initialize));
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) -> quarantineHeld());
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.GameShuttingDownEvent event) -> reset());
    }

    private static void initialize() {
        if (initialized) return;
        var owned = Collections.newSetFromMap(new IdentityHashMap<IKeybind, Boolean>());
        int fields = 0;
        var declarations = ConfigScreenSettings.INSTANCE.getConfigs();
        if (declarations.size() > 64) throw new IllegalStateException("IPN settings exceed the input-owner budget");
        for (Object entry : declarations) {
            var declaration = (ConfigDeclaration) entry;
            for (var option : declaration.getBuilder().getInnerConfig().getConfigOptionList()) {
                if (++fields > 4096) throw new IllegalStateException("IPN settings exceed the input-owner budget");
                if (option instanceof ConfigHotkey hotkey) owned.add(hotkey.getRealMainKeybind());
            }
        }
        if (owned.isEmpty()) throw new IllegalStateException("IPN did not expose its native shortcut owners");
        if (LoadingModList.get().getModFileById("emi") != null) {
            // Version was admitted before target mixins. Resolve this public optional API once;
            // no reflective screen/widget traversal occurs during input, ticks, or rendering.
            try {
                var handle = java.lang.invoke.MethodHandles.publicLookup().findStatic(
                        Class.forName("dev.emi.emi.api.EmiApi"), "isSearchFocused", java.lang.invoke.MethodType.methodType(boolean.class));
                overlayFocus = () -> {
                    try { return (boolean)handle.invokeExact(); }
                    catch (Throwable failure) { throw new IllegalStateException("Cannot observe the admitted EMI search focus", failure); }
                };
            } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Cannot bind the admitted EMI search focus API", failure); }
        }
        OWNED.addAll(owned);
        initialized = true;
    }

    private static boolean owns(IKeybind keybind) {
        initialize();
        for (int depth = 0; depth < 8; depth++) {
            if (OWNED.contains(keybind)) return true;
            if (!(keybind instanceof AlternativeKeybind alternative)) return false;
            keybind = alternative.getParent();
        }
        return false;
    }

    public static boolean allowed(IKeybind keybind) {
        if (!owns(keybind) || assigning()) return true;
        var mc = Minecraft.getInstance();
        boolean text = textOwnsKeyboard();
        var upstream = GlobalInputHandler.INSTANCE;
        POLICY.observe(upstream.getPressedKeys(), text, mc.isWindowActive());
        boolean slot = upstream.getLastKey() < 0 && mouseOverCurrentSlot(mc);
        return POLICY.allows(keybind.getKeyCodes(), text, slot, mc.isWindowActive());
    }

    private static boolean mouseOverCurrentSlot(Minecraft mc) {
        if (!(mc.screen instanceof AbstractContainerScreen<?> container)) return false;
        var slot = container.getSlotUnderMouse();
        if (slot == null || !slot.isActive()) return false;
        double x = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
        double y = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();
        // Native hover state comes from rendering. Recheck its exact geometry against the current
        // pointer so movement onto a text field between render and input cannot reuse a stale slot.
        return ((IpnContainerAccess)container).skyweave$hovering(slot.x, slot.y, 16, 16, x, y);
    }

    public static boolean inventoryMouseEvent() {
        var mc = Minecraft.getInstance();
        return mc.isWindowActive() && GlobalInputHandler.INSTANCE.getLastKey() < 0 && mouseOverCurrentSlot(mc);
    }

    public static void observe() {
        if (!assigning()) POLICY.observe(GlobalInputHandler.INSTANCE.getPressedKeys(), textOwnsKeyboard(), Minecraft.getInstance().isWindowActive());
    }

    public static void completed() { POLICY.completed(GlobalInputHandler.INSTANCE.getPressedKeys()); }
    public static void reset() { POLICY.reset(); }
    /** Keep held origins quarantined through focus loss/reconnect until native release cleanup. */
    public static void quarantineHeld() { POLICY.observe(GlobalInputHandler.INSTANCE.getPressedKeys(), true, false); }

    private static boolean assigning() {
        return GlobalInputHandler.INSTANCE.getCurrentAssigningKeybind() != null;
    }

    private static boolean textOwnsKeyboard() {
        var screen = Minecraft.getInstance().screen;
        if (screen == null) return false;
        if (screen instanceof AbstractSignEditScreen || screen instanceof BookEditScreen || overlayFocus.getAsBoolean()) return true;
        if (screen instanceof ConfigScreenBase config && config.getSearchBox().getTextField().getVanillaFocused()) return true;
        if (screen instanceof BaseScreen custom && libraryTextFocused(custom)) return true;
        if (screen instanceof RecipeUpdateListener recipe && recipe.getRecipeBookComponent().isVisible()) {
            var search = ((RecipeSearchAccess)recipe.getRecipeBookComponent()).skyweave$searchBox();
            if (search != null && search.visible && search.isFocused()) return true;
        }
        GuiEventListener current = screen;
        // Follow one native focused-child path. A cycle or excessive depth refuses the shortcut.
        Set<GuiEventListener> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int depth = 0; depth < 32; depth++) {
            if (!visited.add(current)) return true;
            if (current instanceof EditBox text && text.visible && text.isFocused()
                    || current instanceof MultiLineEditBox multiline && multiline.visible && multiline.isFocused()) return true;
            if (!(current instanceof ContainerEventHandler container) || container.getFocused() == null) return false;
            current = container.getFocused();
        }
        return true;
    }

    private static boolean libraryTextFocused(BaseScreen screen) {
        org.anti_ad.mc.common.gui.widgets.Widget current = screen.getRootWidget();
        var visited = Collections.newSetFromMap(new IdentityHashMap<org.anti_ad.mc.common.gui.widgets.Widget, Boolean>());
        for (int depth = 0; depth < 32; depth++) {
            if (!visited.add(current)) return true;
            if (current.getVisible() && current instanceof org.anti_ad.mc.common.gui.widgets.glue.ITextFieldWidget text
                    && text.getVanillaFocused()) return true;
            var focused = current.getFocusedWidget();
            if (focused == null) return false;
            current = focused;
        }
        return true;
    }
}
