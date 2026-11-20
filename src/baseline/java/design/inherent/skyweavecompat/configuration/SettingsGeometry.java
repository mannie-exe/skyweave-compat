package design.inherent.skyweavecompat.configuration;

import java.util.ArrayList;
import java.util.List;

/** Measured form geometry without widget, renderer, or file ownership. */
public final class SettingsGeometry {
    public record Size(int width, int height) {
        public Size { if (width < 1 || height < 1) throw new IllegalArgumentException("Invalid control extent"); }
    }
    public record Placement(int x, int y, int width, int height) {}
    public record Controls(List<Placement> placements, int height) {
        public Controls { placements = List.copyOf(placements); }
    }
    public static boolean pair(String group, int firstMinimum, String nextGroup, int secondMinimum, int available, int gap) {
        return group != null && !group.isBlank() && group.equals(nextGroup) && firstMinimum > 0 && secondMinimum > 0
                && 2L * Math.max(firstMinimum, secondMinimum) + gap <= available;
    }
    public static Controls controls(List<Size> sizes, int width, int gap) {
        if (width < 1 || gap < 0) throw new IllegalArgumentException("Invalid form bounds");
        var positions = new ArrayList<Placement>();
        int x = 0, y = 0, lineHeight = 0;
        for (var size : sizes) {
            int controlWidth = Math.min(width, size.width());
            if (x > 0 && (long) x + controlWidth > width) { y += lineHeight + gap; x = 0; lineHeight = 0; }
            positions.add(new Placement(x, y, controlWidth, size.height()));
            x += controlWidth + gap; lineHeight = Math.max(lineHeight, size.height());
        }
        return new Controls(positions, y + lineHeight);
    }
    private SettingsGeometry() {}
}
