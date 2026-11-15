package design.inherent.skyweavecompat.clientprobe;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import design.inherent.skyweavecompat.Feature;
import design.inherent.skyweavecompat.OptionalIntegrations;
import design.inherent.skyweavecompat.client.MainMenuIdentity;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.PlainTextButton;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.internal.BrandingControl;

/** Read native cached branding and request a real framebuffer image in the owned menu fixture. */
final class ClientMainMenuProbe {
    private ClientMainMenuProbe() {}

    static void observe(Path root, Minecraft mc, JsonObject command) throws Exception {
        if (!Boolean.getBoolean("skyweave_compat.probe.branding") || mc.level != null || mc.getOverlay() != null
                || !(mc.screen instanceof TitleScreen)) throw new IllegalStateException("Expected the disconnected branding fixture title screen");
        var report = new JsonObject();
        String expected = MainMenuIdentity.line();
        boolean enabled = OptionalIntegrations.enabled(Feature.MAIN_MENU_IDENTITY);
        var forward = lines(true, false);
        var reverse = new ArrayList<>(forward); Collections.reverse(reverse);
        if (!reverse.equals(lines(true, true))) throw new AssertionError("Native reverse branding order changed");
        var noMinecraft = forward.subList(1, forward.size());
        if (!noMinecraft.equals(lines(false, false))) throw new AssertionError("Native no-Minecraft branding view changed");
        if (forward.stream().filter(expected::equals).count() != (enabled ? 1 : 0))
            throw new AssertionError("Missing or duplicate optional menu identity: " + forward);
        report.addProperty("sequence", command.get("sequence").getAsInt());
        report.addProperty("enabled", enabled);
        report.addProperty("expectedIdentity", expected);
        report.add("brandings", new GsonBuilder().create().toJsonTree(forward));
        report.addProperty("compatVersion", ModList.get().getModContainerById("skyweave_compat").orElseThrow().getModInfo().getVersion().toString());
        ModList.get().getModContainerById("skyweave_resource_patches").ifPresent(mod ->
                report.add("packProperties", new GsonBuilder().create().toJsonTree(mod.getModInfo().getModProperties())));
        report.addProperty("modCount", ModList.get().size());
        report.addProperty("modernFixPresent", ModList.get().isLoaded("modernfix"));
        report.addProperty("guiWidth", mc.getWindow().getGuiScaledWidth());
        report.addProperty("guiHeight", mc.getWindow().getGuiScaledHeight());
        report.addProperty("guiScale", mc.getWindow().getGuiScale());
        report.addProperty("language", mc.options.languageCode);
        report.addProperty("forceUnicodeFont", mc.options.forceUnicodeFont().get());
        report.addProperty("fontLineHeight", mc.font.lineHeight);
        var widths = new JsonArray();
        for (String line : forward) widths.add(mc.font.width(line));
        report.add("nativeFontWidths", widths);
        var controls = new JsonArray();
        for (var child : mc.screen.children()) {
            if (!(child instanceof AbstractWidget widget) || !widget.visible) continue;
            if (widget instanceof PlainTextButton) {
                report.addProperty("nativeCopyrightWidth", widget.getWidth());
                continue;
            }
            var control = new JsonObject();
            control.addProperty("label", widget.getMessage().getString());
            control.addProperty("x", widget.getX()); control.addProperty("y", widget.getY());
            control.addProperty("width", widget.getWidth()); control.addProperty("height", widget.getHeight());
            controls.add(control);
            if (enabled) for (int i = 0; i < reverse.size(); i++) {
                int y = mc.getWindow().getGuiScaledHeight() - 10 - i * (mc.font.lineHeight + 1);
                if (2 < widget.getRight() && 2 + mc.font.width(reverse.get(i)) > widget.getX()
                        && y < widget.getBottom() && y + mc.font.lineHeight > widget.getY())
                    throw new AssertionError("Branding overlaps native control: " + control + " / " + reverse.get(i));
            }
        }
        report.add("controls", controls);
        var field = BrandingControl.class.getDeclaredField("brandings"); field.setAccessible(true);
        report.addProperty("cacheIdentity", System.identityHashCode(field.get(null)));
        if (command.has("capture")) {
            String name = command.get("capture").getAsString();
            if (!name.matches("[A-Za-z0-9_-]{1,60}")) throw new IllegalArgumentException("Expected a bounded capture name");
            Screenshot.grab(mc.gameDirectory, "branding-" + name + ".png", mc.getMainRenderTarget(), ignored -> {});
            report.addProperty("captureRequested", "branding-" + name + ".png");
        }
        ProbeStateFiles.write(root.resolve("menu-identity-observation.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }

    private static List<String> lines(boolean includeMinecraft, boolean reverse) {
        var values = new ArrayList<String>();
        BrandingControl.forEachLine(includeMinecraft, reverse, (index, text) -> {
            if (index != values.size()) throw new AssertionError("Non-sequential native branding callback");
            values.add(text);
        });
        return List.copyOf(values);
    }
}
