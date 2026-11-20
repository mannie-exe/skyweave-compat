package design.inherent.skyweavecompat.configuration;

import java.util.LinkedHashMap;
import java.util.Map;

/** Section positions come from measured rows; an explicit clamped jump keeps its destination selected. */
public final class SectionNavigation {
    public record Bookmark(String section, float offset, boolean end) {
        public Bookmark(String section, float offset) { this(section, offset, false); }
    }
    private final Map<String, Integer> anchors = new LinkedHashMap<>();
    private String destination;
    private float destinationScroll;

    public void add(String id, int y) {
        if (anchors.putIfAbsent(id, y) != null) throw new IllegalArgumentException("Duplicate section: " + id);
    }
    public float jump(String id, float maximum) {
        Integer y = anchors.get(id);
        if (y == null) throw new IllegalArgumentException("Unknown section: " + id);
        destination = id;
        return destinationScroll = Math.clamp((float) y, 0, maximum);
    }
    public String active(float scroll, float maximum) {
        if (destination != null && Math.abs(scroll - destinationScroll) < 0.5f) return destination;
        destination = null;
        String current = null;
        for (var anchor : anchors.entrySet()) {
            if (anchor.getValue() <= scroll + 1) current = anchor.getKey();
        }
        // A manually reached end selects the final section even if its heading cannot reach the top.
        if (maximum > 0 && scroll >= maximum - 0.5f && !anchors.isEmpty()) {
            for (String id : anchors.keySet()) current = id;
        }
        return current != null ? current : anchors.keySet().stream().findFirst().orElse(null);
    }
    public Bookmark remember(float scroll, float maximum) {
        String id = active(scroll, maximum);
        return id == null ? null : new Bookmark(id, scroll - anchors.get(id), maximum > 0 && scroll >= maximum - 0.5f);
    }
    public float restore(Bookmark bookmark, float maximum) {
        destination = null;
        if (bookmark == null || !anchors.containsKey(bookmark.section())) return 0;
        destination = bookmark.section();
        return destinationScroll = bookmark.end() ? maximum : Math.clamp(anchors.get(destination) + bookmark.offset(), 0, maximum);
    }
}
