package design.inherent.skyweavecompat.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import design.inherent.skyweavecompat.mixin.client.input.PowerGridWireAccess;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

/** Native editor admission and one owned alternate-wire press, retaining no world or player. */
public final class PowerGridInput {
    private static InputConstants.Key origin;
    private static KeyModifier modifier;
    private static KeyMapping pending;
    private static final IKeyConflictContext EDITOR = new IKeyConflictContext() {
        public boolean isActive() { return Minecraft.getInstance().screen instanceof PowerGridEditor; }
        // Native NONE must retain semantic Shift (reverse rotation), just as the upstream universal context did.
        public boolean conflicts(IKeyConflictContext other) {
            return other == this || other == KeyConflictContext.IN_GAME || other == KeyConflictContext.UNIVERSAL;
        }
    };
    private PowerGridInput() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) -> cancel());
    }

    public static void context(Object action, KeyMapping mapping) {
        mapping.setKeyConflictContext(alternate(action) ? KeyConflictContext.IN_GAME : EDITOR);
    }

    public static boolean alternate(Object action) {
        return action instanceof Enum<?> value && value.name().equals("ALTERNATE_WIRE_PLACEMENT");
    }

    public static boolean editor(KeyMapping mapping) {
        return Minecraft.getInstance().screen instanceof PowerGridEditor && NativeInputAdmission.active(mapping);
    }

    public static boolean alternate(KeyMapping mapping, InputConstants.Key input, int phase, boolean identity) {
        pending = null;
        if (phase == GLFW.GLFW_RELEASE)
            return origin != null && (origin.equals(input) || modifier.matches(input));
        if (phase != GLFW.GLFW_PRESS || !identity) return false;
        var mc = Minecraft.getInstance();
        if (mc.screen != null || mc.player == null || mc.level == null || !NativeInputAdmission.active(mapping)) return false;
        pending = mapping;
        return true;
    }

    public static void observed(int phase) {
        if (phase == GLFW.GLFW_PRESS && pending != null && PowerGridWireAccess.skyweave$alternate()) {
            origin = pending.getKey(); modifier = pending.getKeyModifier();
        } else if (!PowerGridWireAccess.skyweave$alternate()) {
            origin = null; modifier = null;
        }
        pending = null;
    }

    public static void screenChanged() { if (Minecraft.getInstance().screen != null) cancel(); }

    public static void cancel() {
        if (origin == null) { pending = null; return; }
        origin = null; modifier = null; pending = null;
        var mc = Minecraft.getInstance();
        if (mc.player != null && mc.level != null && mc.getConnection() != null)
            PowerGridWireAccess.skyweave$release(mc, GLFW.GLFW_RELEASE);
        else PowerGridWireAccess.skyweave$alternate(false);
    }
}
