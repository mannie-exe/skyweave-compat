package design.inherent.skyweavecompat.rendering;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

/** Render-thread queue: whole answers are refreshed each frame; deferred requests get first turn next frame. */
public final class CoverageRequests {
    public record Bounds(int cameraX, int cameraZ, int minimumX, int minimumZ, int maximumX, int maximumZ,
                         int minimumY, int maximumY) {}
    @FunctionalInterface public interface Probe { CoverageColumns.State read(CoverageFootprint.Cell cell, Bounds bounds); }
    private static final class Entry {
        final Bounds bounds;
        final CoverageFootprint footprint;
        long frame = -1;
        boolean ready;
        Entry(Bounds bounds, int maximumCells) {
            this.bounds = bounds;
            footprint = CoverageFootprint.create((double) bounds.cameraX * 16, (double) bounds.cameraZ * 16,
                    (double) bounds.minimumX * 16, (double) bounds.minimumZ * 16,
                    (double) bounds.maximumX * 16 + 15, (double) bounds.maximumZ * 16 + 15, maximumCells);
        }
    }
    private final int maximumEntries, maximumCells, frameBudget;
    private final Map<Object, Entry> entries = new LinkedHashMap<>(16, .75f, true);
    private final LinkedHashSet<Object> pending = new LinkedHashSet<>();
    private Object context;
    private long frame = -1, prepared = -1;
    private int remaining, reads;

    public CoverageRequests(int maximumEntries, int maximumCells, int frameBudget) {
        if (maximumEntries < 1 || maximumEntries > 1024 || maximumCells < 1 || maximumCells > 16384
                || frameBudget < maximumCells || frameBudget > 65536) throw new IllegalArgumentException("Invalid coverage budgets");
        this.maximumEntries = maximumEntries; this.maximumCells = maximumCells; this.frameBudget = frameBudget;
    }

    public void begin(long frame, Object context) {
        if (this.context != context) { clear(); this.context = context; }
        if (this.frame != frame) { this.frame = frame; remaining = frameBudget; reads = 0; prepared = -1; }
    }

    public boolean ready(Object id, Bounds bounds, Probe probe) {
        if (context == null || frame < 0 || bounds.minimumY > bounds.maximumY) return false;
        if (prepared != frame) {
            prepared = frame;
            // Copy only the bounded ID queue: get() changes LRU order, and evaluation rotates pending entries.
            for (Object key : new ArrayList<>(pending)) {
                var entry = entries.get(key);
                if (entry != null) evaluate(key, entry, probe);
            }
        }
        var entry = entries.get(id);
        if (entry == null || !entry.bounds.equals(bounds)) {
            entry = new Entry(bounds, maximumCells); entries.put(id, entry); pending.add(id);
            while (entries.size() > maximumEntries) {
                Object eldest = entries.keySet().iterator().next(); entries.remove(eldest); pending.remove(eldest);
            }
        }
        evaluate(id, entry, probe);
        return entry.frame == frame && entry.ready;
    }

    private void evaluate(Object id, Entry entry, Probe probe) {
        if (entry.frame == frame) return;
        if (entry.footprint.status() != CoverageFootprint.Status.VALID) {
            entry.frame = frame; entry.ready = false; pending.remove(id); return;
        }
        if (entry.footprint.cells().size() > remaining) { pending.remove(id); pending.addFirst(id); return; }
        pending.remove(id);
        entry.ready = true;
        for (var cell : entry.footprint.cells()) {
            remaining--; reads++;
            if (probe.read(cell, entry.bounds) == CoverageColumns.State.UNKNOWN) { entry.ready = false; break; }
        }
        entry.frame = frame;
        pending.add(id);
    }

    public void clear() { entries.clear(); pending.clear(); context = null; frame = prepared = -1; remaining = reads = 0; }
    public int reads() { return reads; }
    public int size() { return entries.size(); }
}
