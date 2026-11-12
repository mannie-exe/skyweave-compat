package design.inherent.skyweavecompat.climate;

import java.util.List;

/** Compact thermal intervals; contains no blocks, generator or hidden seed. */
public record ClimateColumn(List<Layer> layers) {
    public static final int MAX_LAYERS = 32;
    public static final ClimateColumn EMPTY = new ClimateColumn(List.of());
    /** An unavailable capture grants no publication authority; callers must not publish a partial column. */
    public static final class Unavailable extends RuntimeException {
        public enum Reason { UNKNOWN, BUDGET }
        private final Reason reason;
        public Unavailable(Reason reason) { super("Natural climate capture unavailable: " + reason);this.reason=reason; }
        public Reason reason() { return reason; }
    }
    public record Layer(int bottom, int top, int datum) {
        public Layer {
            if (bottom < -2048 || top >= 2048 || bottom > top || datum < -2048 || datum >= 2048)
                throw new IllegalArgumentException("Invalid climate contributor bounds");
        }
        public long distance(int y) { return y < bottom ? (long)bottom - y : y > top ? (long)y - top : 0; }
    }
    public ClimateColumn {
        layers = List.copyOf(layers);
        if (layers.size() > MAX_LAYERS) throw new IllegalArgumentException("Too many climate contributors");
    }
    public Integer referenceY(int y, ClimateProfile policy) {
        Layer selected = null;
        for (var layer : layers) {
            long distance = layer.distance(y);
            if (distance > policy.airReach()) continue;
            if (selected == null || distance < selected.distance(y)
                    || distance == selected.distance(y) && Math.abs((long)y - layer.datum()) < Math.abs((long)y - selected.datum()))
                selected = layer;
        }
        if (selected == null) return null;
        return referenceY(y,selected.datum(),policy);
    }
    public static int referenceY(int y,int datum,ClimateProfile policy) {
        double reference = 64 + policy.altitudeScale() * ((double)y - datum);
        return (int)Math.clamp(Math.floor(reference), -2048, 2047);
    }
}
