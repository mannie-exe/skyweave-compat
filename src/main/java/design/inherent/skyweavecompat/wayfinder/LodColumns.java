package design.inherent.skyweavecompat.wayfinder;

import java.util.List;

/** Query-local observed columns; missing information is never interpreted as air. */
public interface LodColumns extends AutoCloseable {
    enum State { READY, PENDING, MISSING, CLOSED, FAILED }
    record Band(int bottom, int top, boolean air) {}
    record Column(State state, List<Band> bands) {
        public Column { bands = List.copyOf(bands); }
        public static Column state(State state) { return new Column(state, List.of()); }
    }
    Column poll(int blockX, int blockZ);
    @Override void close();
}
