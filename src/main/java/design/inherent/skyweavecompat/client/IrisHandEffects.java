package design.inherent.skyweavecompat.client;

import design.inherent.skyweavecompat.mixin.client.OutlineGlowAccess;
import design.inherent.skyweavecompat.mixin.client.OutlineLayersAccess;
import design.inherent.skyweavecompat.mixin.client.OutlineMoldAccess;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.shadows.ShadowRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import static org.lwjgl.opengl.GL11C.glGetInteger;
import static org.lwjgl.opengl.GL30C.GL_DRAW_FRAMEBUFFER_BINDING;

/** Native effect callbacks, tied to actual Iris hand passes and one completed world frame. */
public final class IrisHandEffects {
    private static final HandEffectFrame FRAME = new HandEffectFrame();
    private static Pass current;
    private static boolean nativeCallback;
    private IrisHandEffects() {}

    public static boolean active() {
        return Minecraft.getInstance().level != null && Iris.isPackInUseQuick() && !ShadowRenderer.ACTIVE;
    }

    public static boolean nativeCallback() { return nativeCallback; }

    public static void begin(Object pipeline) {
        OutlineGlowAccess.skyweave$captured(false);
        FRAME.begin(pipeline, Minecraft.getInstance().level);
    }

    public static Pass pass(Object pipeline) {
        Pass previous = current;
        return current = new Pass(previous, pipeline);
    }

    /** Called after Iris's own eligibility guard, before geometry enters its buffered hand pass. */
    public static void enter(Object pipeline) {
        if (!active() || current == null || current.pipeline != pipeline) return;
        if (!FRAME.matches(pipeline, Minecraft.getInstance().level)) return;
        if (current.entered) throw new IllegalStateException("Repeated Iris hand entry");
        current.previousHand = OutlineMoldAccess.skyweave$hand();
        boolean first = FRAME.enter(pipeline, Minecraft.getInstance().level);
        current.entered = true;
        OutlineMoldAccess.skyweave$hand(true);
        if (first) callback(OutlineGlowAccess::skyweave$begin);
    }

    public static void finish(Object pipeline) {
        try {
            if (FRAME.finish(pipeline, Minecraft.getInstance().level) && OutlineGlowAccess.skyweave$captured())
                callback(OutlineGlowAccess::skyweave$end);
        } finally {
            OutlineGlowAccess.skyweave$captured(false);
            FRAME.clear();
        }
    }

    public static void clear(Object pipeline) {
        FRAME.clear();
        OutlineGlowAccess.skyweave$captured(false);
    }

    private static void callback(Runnable action) {
        boolean previous = nativeCallback;
        nativeCallback = true;
        try (var state = new EffectGlState()) { action.run(); }
        finally { nativeCallback = previous; }
    }

    public static boolean program(ShaderInstance shader) {
        if (!active() || shader == null) return false;
        return shader == OutlineLayersAccess.skyweave$outline() || shader == OutlineLayersAccess.skyweave$rune()
                || shader == OutlineGlowAccess.skyweave$down() || shader == OutlineGlowAccess.skyweave$blur()
                || shader == OutlineGlowAccess.skyweave$composite();
    }

    public static boolean independentTarget(ShaderInstance shader) {
        if (shader == OutlineGlowAccess.skyweave$down() || shader == OutlineGlowAccess.skyweave$blur()
                || shader == OutlineGlowAccess.skyweave$composite()) return true;
        var mask = OutlineGlowAccess.skyweave$mask();
        return mask != null && glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING) == mask.frameBufferId;
    }

    public static final class Pass implements AutoCloseable {
        private final Pass previous;
        private final Object pipeline;
        private boolean entered, success, closed, previousHand;
        private Pass(Pass previous, Object pipeline) { this.previous = previous; this.pipeline = pipeline; }
        public void succeeded() { success = true; }
        @Override public void close() {
            if (closed) return;
            closed = true;
            try {
                if (entered) {
                    OutlineMoldAccess.skyweave$hand(previousHand);
                    FRAME.leave(success);
                    if (!success) OutlineGlowAccess.skyweave$captured(false);
                }
            } finally { current = previous; }
        }
    }
}
