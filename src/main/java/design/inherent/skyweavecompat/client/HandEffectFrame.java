package design.inherent.skyweavecompat.client;

/** A render-thread frame receipt, shared by the solid/translucent hands and one final composite. */
public final class HandEffectFrame {
    private Object pipeline, level;
    private int depth;
    private boolean initialized, completed, failed;

    public void begin(Object pipeline, Object level) {
        clear();
        this.pipeline = pipeline;
        this.level = level;
    }

    /** True only for the first eligible hand pass of this frame. */
    public boolean enter(Object pipeline, Object level) {
        if (!matches(pipeline, level) || completed || failed) throw new IllegalStateException("No active hand-effect frame");
        depth++;
        boolean first = !initialized;
        initialized = true;
        return first;
    }

    public void leave(boolean success) {
        if (depth == 0) throw new IllegalStateException("Unbalanced hand-effect pass");
        failed |= !success;
        depth--;
    }

    public boolean finish(Object pipeline, Object level) {
        if (!matches(pipeline, level) || completed || depth != 0 || failed) return false;
        completed = true;
        return initialized;
    }

    public boolean matches(Object pipeline, Object level) {
        return pipeline != null && level != null && this.pipeline == pipeline && this.level == level;
    }

    public void clear() {
        pipeline = level = null;
        depth = 0;
        initialized = completed = failed = false;
    }
}
