package design.inherent.skyweavecompat.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import design.inherent.skyweavecompat.mixin.client.input.EffortlessKeysAccess;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

/** Retains one admitted press across the native radial's deliberate KeyMapping.releaseAll. */
public final class EffortlessRadialInput {
    private static InputConstants.Key origin;
    private static KeyModifier modifier;
    private EffortlessRadialInput() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) -> reset());
    }

    public static void screenChanged() {
        var screen = Minecraft.getInstance().screen;
        if (screen != null && !(screen instanceof EffortlessRadialScreen)) reset();
    }

    public static void keyboard(long window, int key, int scan, int action) {
        if (window != Minecraft.getInstance().getWindow().getWindow()) return;
        var mapping = EffortlessKeysAccess.skyweave$radial();
        if (mapping.matches(key, scan)) event(mapping, action);
    }

    public static void mouse(long window, int button, int action) {
        if (window != Minecraft.getInstance().getWindow().getWindow()) return;
        var mapping = EffortlessKeysAccess.skyweave$radial();
        if (mapping.matchesMouse(button)) event(mapping, action);
    }

    private static void event(KeyMapping mapping, int action) {
        if (action == GLFW.GLFW_RELEASE) { reset(); return; }
        if (action != GLFW.GLFW_PRESS) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || !mapping.isDown() || !NativeInputAdmission.active(mapping)) { reset(); return; }
        origin = mapping.getKey(); modifier = mapping.getKeyModifier();
    }

    public static boolean down(KeyMapping mapping) {
        var mc = Minecraft.getInstance();
        if (origin == null) return false;
        if (!origin.equals(mapping.getKey()) || modifier != mapping.getKeyModifier() || mc.player == null
                || mc.screen != null && !(mc.screen instanceof EffortlessRadialScreen)
                || !NativeInputAdmission.active(mapping)) {
            reset(); return false;
        }
        return true;
    }

    public static void reset() { origin = null; modifier = null; }
}
